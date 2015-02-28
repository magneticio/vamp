package io.magnetic.vamp_core.rest_api

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.Bootstrap
import spray.can.Http

import scala.concurrent.duration._

object RestApiBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load()
    val server = actorSystem.actorOf(Props[HttpServer], "server-actor")
    val interface = config.getString("server.interface")
    val port = config.getInt("server.port")

    implicit val timeout = Timeout(config.getInt("server.response.timeout").seconds)

    IO(Http)(actorSystem) ? Http.Bind(server, interface, port)
  }
}
