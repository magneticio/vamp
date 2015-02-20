package io.magnetic.vamp_core.bootstrap

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core.rest_api.RestApiRoute
import io.magnetic.vamp_core.rest_api.util.ActorRefFactoryProviderForActors
import spray.can.Http
import spray.routing.HttpService

import scala.concurrent.duration._
import scala.language.postfixOps

object Bootstrap extends App {

  val system = ActorSystem("vamp-core")
  val config = system.settings.config

  val server = system.actorOf(Props[ServerActor], "server-actor")
  val interface = config.getString("server.interface")
  val port = config.getInt("server.port")

  implicit val timeout = Timeout(config.getInt("server.response.timeout").seconds)
  
  IO(Http)(system) ? Http.Bind(server, interface, port)
}

class ServerActor extends HttpService with Actor with ActorLogging with ActorRefFactoryProviderForActors {
  def receive = runRoute(new RestApiRoute(actorRefFactory.dispatcher).route)
}