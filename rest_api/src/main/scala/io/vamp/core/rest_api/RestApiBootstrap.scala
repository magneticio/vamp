package io.vamp.core.rest_api

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import spray.can.Http

object RestApiBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load().getConfig("vamp.core.rest-api")
    val interface = config.getString("interface")
    val port = config.getInt("port")

    val server = ActorSupport.actorOf(HttpServerActor)

    implicit val timeout = HttpServerActor.timeout

    IO(Http)(actorSystem) ? Http.Bind(server, interface, port)
  }
}
