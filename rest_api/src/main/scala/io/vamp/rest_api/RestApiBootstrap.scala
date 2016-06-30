package io.vamp.rest_api

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import io.vamp.common.config.Config
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import spray.can.Http

object RestApiBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem) = IoC.createActor[HttpServerActor] :: Nil

  override def run(implicit actorSystem: ActorSystem) = {

    super.run(actorSystem)

    val config = Config.config("vamp.rest-api")
    val interface = config.string("interface")
    val port = config.int("port")

    val server = IoC.actorFor[HttpServerActor]

    implicit val timeout = HttpServerActor.timeout

    IO(Http)(actorSystem) ? Http.Bind(server, interface, port)
  }
}
