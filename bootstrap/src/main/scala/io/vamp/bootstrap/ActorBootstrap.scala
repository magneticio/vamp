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

  override def start() = {
    info(s"Starting ${getClass.getSimpleName}")
    bootstrap.foreach(_.start)
  }

  override def stop() = {
    info(s"Stopping ${getClass.getSimpleName}")
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    Future.sequence(bootstrap.reverse.map(_.stop))
  }
}

class ActorBootstrap(override val bootstrap: List[ActorBootstrapService])(implicit val actorSystem: ActorSystem, val namespace: Namespace, val timeout: Timeout) extends AbstractActorBootstrap

class RestartableActorBootstrap(namespace: Namespace)(override val bootstrap: List[ActorBootstrapService])(implicit actorSystem: ActorSystem, timeout: Timeout)
    extends ActorBootstrap(bootstrap)(actorSystem, namespace, timeout) {

  implicit val ns: Namespace = namespace

  actorSystem.actorOf(Props(new Actor {
    def receive = {
      case "reload" ⇒ bootstrap.reverse.foreach(_.restart)
      case _        ⇒
    }
  }), s"${namespace.name}-config")
}

class ClassProviderActorBootstrap()(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout)
  extends RestartableActorBootstrap(namespace)(ClassProvider.all[ActorBootstrapService].toList)(actorSystem, timeout)
