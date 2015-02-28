package io.magnetic.vamp_core.rest_api

import akka.actor.ActorLogging
import io.magnetic.vamp_common.notification.NotificationErrorException
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse, Timedout}
import spray.routing._
import spray.util.LoggingContext

class HttpServer extends HttpServiceActor with ActorLogging {

  def exceptionHandler = ExceptionHandler {
    case e: Exception => requestUri { uri =>
      log.error("Request to {} could not be handled normally", uri)
      complete(InternalServerError)
    }
  }

  def rejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, Some(e: NotificationErrorException)) :: _ =>
      complete(BadRequest, "The request content was malformed:\n" + msg)

    case MalformedRequestContentRejection(msg, Some(ex)) :: _ =>
      log.error(ex, ex.getMessage)
      complete(BadRequest, "The request content was malformed.")

    case MalformedRequestContentRejection(msg, None) :: _ =>
      complete(BadRequest, "The request content was malformed.")
  }

  def routingSettings = RoutingSettings.default

  def loggingContext = LoggingContext.fromActorRefFactory

  def handleTimeouts: Receive = {
    case Timedout(x: HttpRequest) =>
      sender() ! HttpResponse(InternalServerError)
  }

  def receive = handleTimeouts orElse runRoute(new RestApiRoute(actorRefFactory).route)(exceptionHandler, rejectionHandler, context, routingSettings, loggingContext)
}
