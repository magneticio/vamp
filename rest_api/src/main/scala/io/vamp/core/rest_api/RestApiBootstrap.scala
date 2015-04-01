package io.vamp.core.rest_api

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap
import spray.can.Http

object RestApiBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load().getConfig("vamp.core.rest-api")
    val server = actorSystem.actorOf(Props[HttpServer], "server-actor")
    val interface = config.getString("interface")
    val port = config.getInt("port")

    implicit val timeout = HttpServer.timeout

    IO(Http)(actorSystem) ? Http.Bind(server, interface, port)
  }
}
