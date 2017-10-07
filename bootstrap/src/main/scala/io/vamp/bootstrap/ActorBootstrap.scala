package io.vamp.bootstrap

import akka.actor.{ Actor, ActorNotFound, ActorSystem, Props }
import akka.util.Timeout
import io.vamp.common.akka.{ Bootstrap, ActorBootstrap ⇒ ActorBootstrapService }
import io.vamp.common.{ ClassProvider, Namespace }

import scala.concurrent.{ ExecutionContext, Future }

trait AbstractActorBootstrap extends Bootstrap {

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  protected def bootstrap: List[ActorBootstrapService]

  override def start(): Future[Unit] = {
    info(s"Starting ${getClass.getSimpleName}")
    val all = bootstrap
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    all.tail.foldLeft[Future[Unit]](all.head.start)((f, b) ⇒ f.flatMap(_ ⇒ b.start))
  }

  override def stop(): Future[Unit] = {
    info(s"Stopping ${getClass.getSimpleName}")
    val all = bootstrap.reverse
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    all.tail.foldLeft[Future[Unit]](all.head.stop)((f, b) ⇒ f.flatMap(_ ⇒ b.stop))
  }
}

class ActorBootstrap(override val bootstrap: List[ActorBootstrapService])(implicit val actorSystem: ActorSystem, val namespace: Namespace, val timeout: Timeout) extends AbstractActorBootstrap

class RestartableActorBootstrap(namespace: Namespace)(override val bootstrap: List[ActorBootstrapService])(implicit actorSystem: ActorSystem, timeout: Timeout)
    extends ActorBootstrap(bootstrap)(actorSystem, namespace, timeout) {

  implicit val ns: Namespace = namespace
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private val name = s"${namespace.name}-config"

  actorSystem.actorSelection(name).resolveOne().failed.foreach {
    case _: ActorNotFound ⇒
      actorSystem.actorOf(Props(new Actor {
        def receive: Actor.Receive = {
          case "reload" ⇒ bootstrap.reverse.foreach(_.restart)
          case _        ⇒
        }
      }), name)
    case _ ⇒
  }
}

class ClassProviderActorBootstrap()(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout)
  extends RestartableActorBootstrap(namespace)(ClassProvider.all[ActorBootstrapService].toList)(actorSystem, timeout)
