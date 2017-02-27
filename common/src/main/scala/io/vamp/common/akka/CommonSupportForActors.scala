package io.vamp.common.akka

import akka.actor.{ Actor, ActorLogging, ActorSystem }
import io.vamp.common.Namespace

trait CommonSupportForActors
    extends Actor
    with ReplyActor
    with ActorLogging
    with ActorExecutionContextProvider
    with CommonProvider {

  implicit lazy val actorSystem: ActorSystem = context.system

  implicit lazy val namespace: Namespace = context.parent.path.elements match {
    case "user" :: ns :: _ ⇒ ns
    case other             ⇒ throw new RuntimeException(s"No namespace for: $other")
  }
}
