package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.http_api.ws.WebSocketActor
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

object HttpApiBootstrap extends ActorBootstrap {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private var binding: Option[Future[ServerBinding]] = None

  private val (interface, port) = (Config.string("vamp.http-api.interface"), Config.int("vamp.http-api.port"))

  def createActors(implicit actorSystem: ActorSystem) = IoC.createActor[WebSocketActor] :: Nil

  override def run(implicit actorSystem: ActorSystem) = {

    super.run

    implicit lazy val materializer = ActorMaterializer()

    implicit lazy val executionContext = actorSystem.dispatcher

    logger.info(s"Binding: $interface:$port")

    binding = Option {
      Http().bindAndHandle(new HttpApiRoute().allRoutes, interface, port)
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

    super.shutdown
  }
}
