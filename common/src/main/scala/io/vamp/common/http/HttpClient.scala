package io.vamp.common.http

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ExecutionException

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.util.Timeout
import com.ning.http.client.{ AsyncCompletionHandler, Response }
import com.typesafe.scalalogging.Logger
import dispatch.Http
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect._
import scala.util.Try

case class HttpClientException(statusCode: Option[Int], message: String) extends RuntimeException(message) {}

object HttpClient {

  val acceptEncodingIdentity: (String, String) = "accept-encoding" → "identity"

  val jsonHeaders: List[(String, String)] = List("Accept" → "application/json", "Content-Type" → "application/json")

  def basicAuthorization(user: String, password: String, headers: List[(String, String)] = jsonHeaders): List[(String, String)] = {
    if (!user.isEmpty && !password.isEmpty) {
      val credentials = Base64.getEncoder.encodeToString(s"$user:$password".getBytes(StandardCharsets.UTF_8))
      ("Authorization" → s"Basic $credentials") :: headers
    }
    else headers
  }
}

class HttpClient(implicit val timeout: Timeout, val system: ActorSystem, formats: Formats = DefaultFormats) {

  import HttpClient._

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private lazy val httpClient = Http.configure(_ setFollowRedirects true)

  def get[A](url: String, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.GET, url, None, headers, logError)
  }

  def post[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.POST, url, body, headers, logError)
  }

  def put[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](HttpMethods.PUT, url, body, headers, logError)
  }

  def delete(url: String, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext) = {
    http[Any](HttpMethods.DELETE, url, None, headers, logError)
  }

  def http[A](method: HttpMethod, uri: String, body: Any, headers: List[(String, String)] = jsonHeaders, logError: Boolean = true)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A]): Future[A] = {
    val requestLog = s"[${method.value} $uri]"
    val requestWithUrl = dispatch.url(uri).setMethod(method.value)
    val requestWithHeaders = headers.foldLeft(requestWithUrl)((http, header) ⇒ http.setHeader(header._1, header._2))
    val requestWithBody = bodyAsString(body) match {
      case Some(some) ⇒
        logger.trace(s"req $requestLog - $some")
        requestWithHeaders.setBody(some.toString)
      case None ⇒
        logger.trace(s"req $requestLog")
        requestWithHeaders
    }

    httpClient(requestWithBody.toRequest → new AsyncCompletionHandler[A] {
      def onCompleted(response: Response) = response.getStatusCode match {
        case status if status / 100 == 2 && (classTag[A].runtimeClass == classOf[Nothing] || classTag[A].runtimeClass == classOf[String]) ⇒
          val body = response.getResponseBody
          logger.trace(s"rsp $requestLog - $body")
          body.asInstanceOf[A]

        case status if status / 100 == 2 ⇒
          val json = dispatch.as.json4s.Json(response)
          Try(logger.trace(s"rsp $requestLog - ${compact(render(json))}"))
          json.extract[A](formats, mf)

        case status ⇒
          val message = s"rsp $requestLog - unexpected status code: $status"
          if (logError) logger.error(message)
          logger.trace(s"$message, for response: ${response.getResponseBody}")
          throw HttpClientException(Some(status), response.getResponseBody)
      }
    }).recover {
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
