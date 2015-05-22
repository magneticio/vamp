package io.vamp.common.http

import io.vamp.common.akka.CommonActorSupport
import io.vamp.common.notification.NotificationErrorException
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse, StatusCode, Timedout}
import spray.httpx.marshalling.ToResponseMarshaller
import spray.routing._
import spray.util.LoggingContext

trait HttpServerBaseActor extends HttpServiceActor with CommonActorSupport {

  def route: Route

  implicit def marshaller: ToResponseMarshaller[Any]

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

    case MalformedHeaderRejection(headerName, msg, cause) :: _ =>
      respondWithError(BadRequest, s"$msg")
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

    respondWithStatus(code) {
      complete("message" -> response)
    }
  }
}
