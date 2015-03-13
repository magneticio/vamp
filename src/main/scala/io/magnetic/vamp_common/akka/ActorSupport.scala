package io.magnetic.vamp_common.akka

import akka.actor._
import io.magnetic.vamp_common.text.Text

trait ActorDescription {
  def props(args: Any*): Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {
  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit actorSystem: ActorSystem) = actorSystem.actorOf(actorDescription.props(args: _*), actorDescription.name)
}

trait ActorSupport {
  this: Actor =>

  def actorOf(actorDescription: ActorDescription, args: Any*) = context.system.actorOf(actorDescription.props(args: _*), actorDescription.name)

  def actorFor(actorDescription: ActorDescription) = context.actorSelection(s"../${actorDescription.name}")
}