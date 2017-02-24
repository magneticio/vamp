package io.vamp.bootstrap

import akka.actor.{ Actor, ActorSystem, Props }
import io.vamp.common.{ ClassProvider, Config, Namespace }
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }

import scala.concurrent.{ ExecutionContext, Future }

class ActorBootstrap(implicit val namespace: Namespace) extends Bootstrap {

  private implicit val system = ActorSystem("vamp")

  private implicit val timeout = Config.timeout("vamp.bootstrap.timeout")()

  private lazy val bootstrap = ClassProvider.all[ActorBootstrapService].toList

  override def start() = bootstrap.foreach(_.start)

  override def stop() = {
    implicit val executionContext: ExecutionContext = system.dispatcher
    Future.sequence(bootstrap.reverse.map(_.stop)).foreach(_ ⇒ system.terminate())
  }

  system.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ bootstrap.reverse.foreach(_.restart)
      case _        ⇒
    }
  }), "vamp")
}
