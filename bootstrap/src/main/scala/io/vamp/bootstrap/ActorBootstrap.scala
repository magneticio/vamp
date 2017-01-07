package io.vamp.bootstrap

import akka.actor.{ Actor, ActorSystem, Props }
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.spi.ClassProvider

class ActorBootstrap extends Bootstrap {

  private implicit lazy val system = ActorSystem("vamp")

  private lazy val bootstrap = ClassProvider.all[ActorBootstrapService].toList

  override def run() = bootstrap.foreach(_.run)

  override def shutdown() = {
    bootstrap.reverse.foreach(_.shutdown)
    system.terminate()
  }

  private def reload() = {
    bootstrap.reverse.foreach(_.shutdown)
    bootstrap.foreach(_.run)
  }

  system.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ reload()
      case _        ⇒
    }
  }), "vamp")
}
