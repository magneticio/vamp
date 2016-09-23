package io.vamp.common.http

import java.util.concurrent.ExecutionException

import akka.actor.ActorSystem
import akka.http.javadsl.model.RequestEntity
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ ByteString, Timeout }
import com.typesafe.scalalogging.Logger
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

  val acceptEncodingIdentity: (String, String) = "accept-encoding" -> "identity"

  val jsonHeaders: List[(String, String)] = List("Accept" -> "application/json")

  val jsonContentType = ContentTypes.`application/json`
}

class HttpClient(implicit val timeout: Timeout, val system: ActorSystem, formats: Formats = DefaultFormats) {

  import HttpClient._

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  implicit val executionContext = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

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
      .via(Http().outgoingConnection(requestUri.authority.host.address, requestUri.authority.port))
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

  private def bodyAsString(body: Any)(implicit formats: Formats): Option[String] = body match {
    case string: String                            ⇒ Some(string)
    case Some(string: String)                      ⇒ Some(string)
    case Some(some: AnyRef)                        ⇒ Some(write(some))
    case any: AnyRef if any != null && any != None ⇒ Some(write(any))
    case any if any != null && any != None         ⇒ Some(any.toString)
    case _                                         ⇒ None
  }
}
