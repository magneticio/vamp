package io.vamp.rest_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

object RestApiBootstrap extends ActorBootstrap {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private var binding: Option[Future[ServerBinding]] = None

  private val (interface, port) = (Config.string("vamp.rest-api.interface"), Config.int("vamp.rest-api.port"))

  def createActors(implicit actorSystem: ActorSystem) = Nil

  override def run(implicit actorSystem: ActorSystem) = {

    implicit lazy val materializer = ActorMaterializer()

    implicit lazy val executionContext = actorSystem.dispatcher

    logger.info(s"Binding: $interface:$port")

    binding = Option {
      Http().bindAndHandle(new RestApiRoute().routes, interface, port)
    }
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    binding.foreach {
      _.foreach { server ⇒
        logger.info(s"Unbinding: $interface:$port")
        server.unbind().foreach {
          _ ⇒ Http().shutdownAllConnectionPools()
        }
      }
    }
  }
}
