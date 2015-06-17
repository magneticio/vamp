package io.vamp.common.akka

import akka.actor._
import io.vamp.common.text.Text


trait ActorDescription {
  def props(args: Any*): Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {
  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox", actorSystem: ActorSystem) =
    actorSystem.actorOf(actorDescription.props(args: _*).withMailbox(mailbox), actorDescription.name)

  def actorFor(actorDescription: ActorDescription)(implicit actorSystem: ActorSystem) =
    actorSystem.actorSelection(s"/user/${actorDescription.name}")
}

trait ActorSupport {

  implicit def actorRefFactory: ActorRefFactory

  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox") =
    actorRefFactory.actorOf(actorDescription.props(args: _*).withMailbox(mailbox), actorDescription.name)

  def actorFor(actorDescription: ActorDescription) = actorRefFactory.actorSelection(s"/user/${actorDescription.name}")
}

trait ActorSupportForActors extends ActorSupport {
  this: Actor =>

  def actorRefFactory = context
}