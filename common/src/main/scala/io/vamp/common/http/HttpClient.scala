package io.vamp.common.http

import java.security.cert.X509Certificate
import java.util.concurrent.ExecutionException
import javax.net.ssl.{ KeyManager, SSLContext, X509TrustManager }

import akka.actor.ActorSystem
import akka.http.javadsl.model.RequestEntity
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ ByteString, Timeout }
import com.typesafe.scalalogging.Logger
import io.vamp.common.util.TextUtil
import io.vamp.common.{ Config, Namespace }
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect._
import scala.util.Try

case class HttpClientException(statusCode: Option[Int], message: String) extends RuntimeException(message) {}

object HttpClient {

  val tlsCheck = Config.boolean("vamp.common.http.client.tls-check")

  val acceptEncodingIdentity: (String, String) = "accept-encoding" → "identity"

  val jsonHeaders: List[(String, String)] = List("Accept" → "application/json")

  val jsonContentType = ContentTypes.`application/json`

  def basicAuthorization(user: String, password: String, headers: List[(String, String)] = jsonHeaders): List[(String, String)] = {
    if (!user.isEmpty && !password.isEmpty) {
      val credentials = TextUtil.encodeBase64(s"$user:$password")
      ("Authorization" → s"Basic $credentials") :: headers
    }
    else headers
  }
}

class HttpClient(implicit val timeout: Timeout, val system: ActorSystem, val namespace: Namespace, formats: Formats = DefaultFormats) {

  import HttpClient._

  private val tlsCheck = HttpClient.tlsCheck()

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  implicit val executionContext = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  private lazy val noCheckHttpsConnectionContext = new HttpsConnectionContext({

    object NoCheckX509TrustManager extends X509TrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def getAcceptedIssuers = Array[X509Certificate]()
    }

    val context = SSLContext.getInstance("TLS")
    context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), null)
    context
  })

  def get[A](url: String, headers: List[(String, String)] = jsonHeaders, contentType: ContentType = jsonContentType, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.GET, url, None, headers, jsonContentType, logError)
  }

  def post[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, contentType: ContentType = jsonContentType, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.POST, url, body, headers, jsonContentType, logError)
  }

  def put[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, contentType: ContentType = jsonContentType, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.PUT, url, body, headers, jsonContentType, logError)
  }

  def delete(url: String, headers: List[(String, String)] = jsonHeaders, contentType: ContentType = jsonContentType, logError: Boolean = true)(implicit executor: ExecutionContext) = {
    http[Any](HttpMethods.DELETE, url, None, headers, jsonContentType, logError)
  }

  def http[A](method: HttpMethod, uri: String, body: Any, headers: List[(String, String)] = jsonHeaders, contentType: ContentType = jsonContentType, logError: Boolean = true)(implicit mf: scala.reflect.Manifest[A]): Future[A] = {
    httpWithEntity[A](method, uri, bodyAsString(body).map(some ⇒ HttpEntity.Strict(contentType, ByteString(some))), headers, logError)
  }

  def httpWithEntity[A](method: HttpMethod, uri: String, body: Option[RequestEntity], headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit mf: scala.reflect.Manifest[A]): Future[A] = {

    val requestLog = s"[${method.toString} $uri]"

    val requestUri = Uri(uri)

    val requestHeaders = headers.map { header ⇒ HttpHeader.parse(header._1, header._2) } collect {
      case ParsingResult.Ok(h, _) ⇒ h
    }

    val request = HttpRequest(uri = requestUri.toRelative, method = method, headers = requestHeaders)

    val requestWithEntity = body match {
      case Some(entity) ⇒
        logger.trace(s"req $requestLog - $entity")
        request.withEntity(entity)
      case None ⇒
        logger.trace(s"req $requestLog")
        request
    }

    def recoverWith[T]: PartialFunction[Throwable, T] = {
      case exception: HttpClientException ⇒ throw exception
      case exception: ExecutionException if exception.getCause != null && exception.getCause.getClass == classOf[HttpClientException] ⇒ throw exception.getCause
      case exception ⇒
        val message = s"rsp $requestLog - exception: ${exception.getMessage}"

        if (logError) {
          logger.error(message)
          logger.trace(message, exception)
        }

        throw HttpClientException(None, exception.getMessage).initCause(if (exception.getCause != null) exception.getCause else exception)
    }

    def decode(entity: ResponseEntity): Future[String] = entity.toStrict(timeout.duration).map(_.data.decodeString("UTF-8"))

    Source.single(requestWithEntity)
      .via(outgoingConnection(requestUri))
      .recover(recoverWith)
      .mapAsync(1)({
        case HttpResponse(status, _, entity, _) ⇒ status.intValue() match {

          case code if code / 100 == 2 && (classTag[A].runtimeClass == classOf[Nothing] || classTag[A].runtimeClass == classOf[String]) ⇒
            decode(entity).map { body ⇒
              logger.trace(s"rsp $requestLog - $body")
              body.asInstanceOf[A]
            }

          case code if code / 100 == 2 ⇒
            decode(entity).map({ body ⇒
              val json = Try(parse(StringInput(body), useBigDecimalForDouble = true)).getOrElse(JString(body))
              Try(logger.trace(s"rsp $requestLog - ${Serialization.writePretty(json)}"))
              json.extract[A](formats, mf)
            })

          case code ⇒
            decode(entity).map { body ⇒
              val message = s"rsp $requestLog - unexpected status code: $code"
              if (logError) logger.error(message)
              logger.trace(s"$message, for response: $body")
              throw HttpClientException(Some(code), body)
            }
        }

        case other: AnyRef ⇒ throw new RuntimeException(other.toString)

      }).runWith(Sink.head)
  }

  private def outgoingConnection(uri: Uri) = uri.scheme match {
    case "https" ⇒
      val host = uri.authority.host.address
      val port = if (uri.authority.port > 0) uri.authority.port else 443
      if (tlsCheck)
        Http().outgoingConnectionHttps(host, port)
      else
        Http().outgoingConnectionHttps(host, port, connectionContext = noCheckHttpsConnectionContext)

    case _ ⇒
      val host = uri.authority.host.address
      val port = if (uri.authority.port > 0) uri.authority.port else 80
      Http().outgoingConnection(host, port)
  }

  private def bodyAsString(body: Any)(implicit formats: Formats): Option[String] = body match {
    case string: String                            ⇒ Some(string)
    case Some(string: String)                      ⇒ Some(string)
    case Some(some: AnyRef)                        ⇒ Some(write(some))
    case any: AnyRef if any != null && any != None ⇒ Some(write(any))
    case any if any != null && any != None         ⇒ Some(any.toString)
    case _                                         ⇒ None
  }
}
