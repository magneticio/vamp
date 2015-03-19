package io.vamp.common.akka

import akka.actor._
import io.vamp.common.text.Text


trait ActorDescription {
  def props(args: Any*): Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {
  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit  mailbox: String = "akka.actor.default-mailbox", actorSystem: ActorSystem) =
    actorSystem.actorOf(actorDescription.props(args: _*).withMailbox(mailbox), actorDescription.name)
}

trait ActorSupport {
  this: Actor =>

  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox") =
    context.system.actorOf(actorDescription.props(args: _*).withMailbox(mailbox), actorDescription.name)

  def actorFor(actorDescription: ActorDescription) = context.actorSelection(s"../${actorDescription.name}")
}