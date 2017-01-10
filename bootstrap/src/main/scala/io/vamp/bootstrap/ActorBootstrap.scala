package io.vamp.bootstrap

import akka.actor.{ Actor, ActorSystem, Props }
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.spi.ClassProvider

import scala.concurrent.{ ExecutionContext, Future }

class ActorBootstrap extends Bootstrap {

  private implicit lazy val system = ActorSystem("vamp")

  private lazy val bootstrap = ClassProvider.all[ActorBootstrapService].toList

  override def run() = bootstrap.foreach(_.run)

  override def shutdown() = shutdownActors({ () ⇒ system.terminate() })

  private def reload() = shutdownActors({ () ⇒ bootstrap.foreach(_.run) })

  private def shutdownActors(onShutdown: () ⇒ Unit) = {
    implicit val executionContext: ExecutionContext = system.dispatcher
    Future.sequence(bootstrap.reverse.map(_.shutdown)).foreach(_ ⇒ onShutdown())
  }

  system.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ reload()
      case _        ⇒
    }
  }), "vamp")
}
