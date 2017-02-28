package io.vamp.common.akka

import akka.actor.{ Actor, ActorSystem, DiagnosticActorLogging }
import akka.event.Logging.MDC
import io.vamp.common.Namespace

trait CommonActorLogging extends DiagnosticActorLogging

trait CommonSupportForActors
    extends Actor
    with ReplyActor
    with CommonActorLogging
    with ActorExecutionContextProvider
    with CommonProvider {

  implicit lazy val actorSystem: ActorSystem = context.system

  implicit lazy val namespace: Namespace = context.parent.path.elements match {
    case "user" :: ns :: _ ⇒ ns
    case other             ⇒ throw new RuntimeException(s"No namespace for: $other")
  }

  override def mdc(currentMessage: Any): MDC = Map("namespace" → namespace.id)
}
