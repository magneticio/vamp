package io.vamp.bootstrap

import akka.actor.{ Actor, ActorSystem, Props }
import akka.util.Timeout
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.{ ClassProvider, Namespace }

import scala.concurrent.{ ExecutionContext, Future }

trait AbstractActorBootstrap extends Bootstrap {

  implicit def timeout: Timeout
  implicit def namespace: Namespace
  implicit def actorSystem: ActorSystem

  protected def bootstrap: List[ActorBootstrapService]

  override def start() = bootstrap.foreach(_.start)

  override def stop() = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    Future.sequence(bootstrap.reverse.map(_.stop))
  }
}

class ActorBootstrap(override val bootstrap: List[ActorBootstrapService])(implicit val actorSystem: ActorSystem, val namespace: Namespace, val timeout: Timeout) extends AbstractActorBootstrap

class ClassProviderActorBootstrap(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) extends ActorBootstrap(ClassProvider.all[ActorBootstrapService].toList)(actorSystem, namespace, timeout) {
  actorSystem.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ bootstrap.reverse.foreach(_.restart)
      case _        ⇒
    }
  }), s"$namespace-config")
}
