package io.vamp.core.rest_api

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorDescription, ActorExecutionContextProvider}
import io.vamp.common.notification.NotificationErrorException
import org.json4s.DefaultFormats
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse, StatusCode, Timedout}
import spray.routing._
import spray.util.LoggingContext

import scala.concurrent.duration._

object HttpServerActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.rest-api.response-timeout").seconds)

  def props(args: Any*): Props = Props[HttpServerActor]
}

class HttpServerActor extends HttpServiceActor with ActorLogging with RestApiRoute with ActorExecutionContextProvider {

  implicit val timeout = HttpServerActor.timeout

  def exceptionHandler = ExceptionHandler {
    case e: NotificationErrorException =>
      respondWithError(BadRequest, s"${e.message}")

    case e: Exception => requestUri { uri =>
      log.error(e, "Request to {} could not be handled normally: {}", uri, e.getMessage)
      respondWithError(InternalServerError)
    }
  }

  def rejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, Some(e: NotificationErrorException)) :: _ =>
      respondWithError(BadRequest, s"$msg")

    case MalformedRequestContentRejection(msg, Some(ex)) :: _ =>
      log.error(ex, ex.getMessage)
      respondWithError(BadRequest)

    case MalformedRequestContentRejection(msg, None) :: _ =>
      respondWithError(BadRequest)
  }

  def routingSettings = RoutingSettings.default

  def loggingContext = LoggingContext.fromActorRefFactory

  def handleTimeouts: Receive = {
    case Timedout(x: HttpRequest) =>
      sender() ! HttpResponse(InternalServerError)
  }

  def receive = handleTimeouts orElse runRoute(route)(exceptionHandler, rejectionHandler, context, routingSettings, loggingContext)

  def respondWithError(code: StatusCode, message: String = "") = {
    val base = "The request content was malformed."
    val response = if (message.isEmpty) base else s"$base $message"

    implicit val json4sFormats = DefaultFormats
    complete(code, "message" -> response)
  }
}
