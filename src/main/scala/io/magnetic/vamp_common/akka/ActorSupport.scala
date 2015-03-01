package io.magnetic.vamp_common.akka

import akka.actor._
import io.magnetic.vamp_common.text.Text

trait ActorDescription {
  def props: Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {
  def actorOf(actorDescription: ActorDescription)(implicit actorSystem: ActorSystem) = actorSystem.actorOf(actorDescription.props, actorDescription.name)
}

trait ActorSupport {
  this: Actor =>

  def actorOf(actorDescription: ActorDescription) = context.system.actorOf(actorDescription.props, actorDescription.name)

  def actorFor(actorDescription: ActorDescription) = context.actorSelection(s"../${actorDescription.name}")
}