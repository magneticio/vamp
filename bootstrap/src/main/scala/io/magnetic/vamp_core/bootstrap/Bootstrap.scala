package io.magnetic.vamp_core.bootstrap

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.rest_api.RestApiRoute
import spray.can.Http
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse, Timedout}
import spray.routing._
import spray.util.LoggingContext

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

object Bootstrap extends App {

  val system = ActorSystem("vamp-core")
  val config = ConfigFactory.load()

  runServer

  private def runServer = {

    val server = system.actorOf(Props[ServerActor], "server-actor")
    val interface = config.getString("server.interface")
    val port = config.getInt("server.port")

    implicit val timeout = Timeout(config.getInt("server.response.timeout").seconds)

    IO(Http)(system) ? Http.Bind(server, interface, port)
  }
}

class ServerActor extends HttpServiceActor with ActorLogging {

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