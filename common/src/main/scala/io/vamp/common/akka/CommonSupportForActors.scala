package io.vamp.common.akka

import akka.actor.{ Actor, ActorSystem, DiagnosticActorLogging }
import akka.event.Logging.MDC
import io.vamp.common.{ Namespace, NamespaceProvider }

trait CommonActorLogging extends DiagnosticActorLogging {
  this: NamespaceProvider ⇒
  override def mdc(currentMessage: Any): MDC = Map("namespace" → namespace.name)
}

trait CommonSupportForActors
    extends Actor
    with ReplyActor
    with CommonProvider
    with CommonActorLogging
    with ActorExecutionContextProvider {

  implicit lazy val actorSystem: ActorSystem = context.system

  implicit lazy val namespace: Namespace = context.parent.path.elements match {
    case "user" :: ns :: _ ⇒ Namespace(ns)
    case other             ⇒ throw new RuntimeException(s"No namespace for: $other")
  }
}
