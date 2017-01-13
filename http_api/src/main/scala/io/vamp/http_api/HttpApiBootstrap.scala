package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.http_api.ws.WebSocketActor

import scala.concurrent.Future

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  private val (interface, port) = (Config.string("vamp.http-api.interface")(), Config.int("vamp.http-api.port")())

  def createActors(implicit actorSystem: ActorSystem) = IoC.createActor[WebSocketActor] :: Nil

  override def start(implicit actorSystem: ActorSystem): Unit = {
    super.start

    implicit lazy val materializer = ActorMaterializer()
    implicit lazy val executionContext = actorSystem.dispatcher

    logger.info(s"Binding: $interface:$port")
    binding = Option(Http().bindAndHandle(new HttpApiRoute().allRoutes, interface, port))
  }

  override def restart(implicit actorSystem: ActorSystem) = {}

  override def stop(implicit actorSystem: ActorSystem): Future[Unit] = {
    implicit val executionContext = actorSystem.dispatcher
    binding.map {
      _.flatMap { server ⇒
        logger.info(s"Unbinding: $interface:$port")
        server.unbind().flatMap {
          _ ⇒ Http().shutdownAllConnectionPools()
        }
      }.flatMap { _ ⇒ super.stop }
    } getOrElse super.stop
  }
}
