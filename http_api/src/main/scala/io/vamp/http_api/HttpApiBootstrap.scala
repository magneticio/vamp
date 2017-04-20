package io.vamp.http_api

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.{ Config, Namespace }
import io.vamp.http_api.ws.WebSocketActor

import scala.concurrent.Future

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  protected def routes(implicit namespace: Namespace, actorSystem: ActorSystem, materializer: Materializer): Route = new HttpApiRoute().allRoutes

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    IoC.createActor(Props(classOf[WebSocketActor], true, true)).map(_ :: Nil)(actorSystem.dispatcher)
  }

  override def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    super.start
    val (interface, port) = (Config.string("vamp.http-api.interface")(), Config.int("vamp.http-api.port")())
    implicit lazy val materializer = ActorMaterializer()
    info(s"Binding API: $interface:$port")
    binding = Option(Http().bindAndHandle(routes, interface, port))
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
