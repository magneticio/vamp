package io.vamp.bootstrap

import akka.actor.ActorSystem
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.spi.ClassProvider

class ActorBootstrap extends Bootstrap {

  implicit lazy val system = ActorSystem("vamp")

  override def run() = bootstrap.foreach(_.run)

  override def shutdown() = {
    bootstrap.reverse.foreach(_.shutdown)
    system.terminate()
  }

  private lazy val bootstrap = ClassProvider.all[ActorBootstrapService].toList
}
