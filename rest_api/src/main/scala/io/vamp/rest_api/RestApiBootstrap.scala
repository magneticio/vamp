package io.vamp.rest_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config

import scala.concurrent.{ ExecutionContext, Future }

object RestApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  def createActors(implicit actorSystem: ActorSystem) = Nil

  override def run(implicit actorSystem: ActorSystem) = {

    implicit lazy val materializer = ActorMaterializer()

    implicit lazy val executionContext = actorSystem.dispatcher

    binding = Option {
      Http().bindAndHandle(
        handler = { new RestApiRoute().routes },
        interface = Config.string("vamp.rest-api.interface"),
        port = Config.int("vamp.rest-api.port")
      )
    }
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    binding.foreach(_.foreach(_.unbind().foreach(_ ⇒ Http().shutdownAllConnectionPools())))
  }
}
