package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.http_api.ws.WebSocketActor

import scala.concurrent.Future

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    IoC.createActor[WebSocketActor].map(_ :: Nil)(actorSystem.dispatcher)
  }

  override def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    super.start

    val (interface, port) = (Config.string("vamp.http-api.interface")(), Config.int("vamp.http-api.port")())

    implicit lazy val materializer = ActorMaterializer()
    implicit lazy val executionContext = actorSystem.dispatcher

    info(s"Binding: $interface:$port")
    binding = Option(Http().bindAndHandle(new HttpApiRoute().allRoutes, interface, port))
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {}

  override def stop(implicit actorSystem: ActorSystem, namespace: Namespace): Future[Unit] = {
    implicit val executionContext = actorSystem.dispatcher
    binding.map {
      _.flatMap { server ⇒
        info(s"Unbinding API")
        server.unbind().flatMap {
          _ ⇒ Http().shutdownAllConnectionPools()
        }
      }.flatMap { _ ⇒ super.stop }
    } getOrElse super.stop
  }
}
