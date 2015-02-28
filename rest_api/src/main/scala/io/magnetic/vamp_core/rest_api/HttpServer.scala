package io.magnetic.vamp_core.rest_api

import akka.actor.ActorLogging
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.ActorExecutionContextProvider
import io.magnetic.vamp_common.notification.NotificationErrorException
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse, Timedout}
import spray.routing._
import spray.util.LoggingContext

import scala.concurrent.duration._

object HttpServer {
  lazy val timeout = Timeout(ConfigFactory.load().getInt("server.response.timeout").seconds)
}

class HttpServer extends HttpServiceActor with ActorLogging with RestApiRoute with ActorExecutionContextProvider {

  implicit val timeout = HttpServer.timeout

  def exceptionHandler = ExceptionHandler {
    case e: NotificationErrorException => 
      complete(BadRequest, s"$requestMalformedMessage:\n${e.message}")
      
    case e: Exception => requestUri { uri =>
      log.error(e, "Request to {} could not be handled normally: {}", uri, e.getMessage)
      complete(InternalServerError)
    }
  }

  val requestMalformedMessage = "The request content was malformed"

  def rejectionHandler = RejectionHandler {
    case MalformedRequestContentRejection(msg, Some(e: NotificationErrorException)) :: _ =>
      complete(BadRequest, s"$requestMalformedMessage:\n$msg")

    case MalformedRequestContentRejection(msg, Some(ex)) :: _ =>
      log.error(ex, ex.getMessage)
      complete(BadRequest, requestMalformedMessage)

    case MalformedRequestContentRejection(msg, None) :: _ =>
      complete(BadRequest, requestMalformedMessage)
  }

  def routingSettings = RoutingSettings.default

  def loggingContext = LoggingContext.fromActorRefFactory

  def handleTimeouts: Receive = {
    case Timedout(x: HttpRequest) =>
      sender() ! HttpResponse(InternalServerError)
  }

  def receive = handleTimeouts orElse runRoute(route)(exceptionHandler, rejectionHandler, context, routingSettings, loggingContext)
}
