package io.magnetic.vamp_core.rest_api.util

import akka.actor._

trait ActorCreationSupport {
  def createActor(props: Props, name: String): ActorRef

  def getActor(name: String): Option[ActorRef]

  def getOrCreateActor(props: Props, name: String): ActorRef = getActor(name).getOrElse(createActor(props, name))
}

trait ActorCreationSupportForActors extends ActorCreationSupport {
  this: Actor ⇒
  def createActor(props: Props, name: String): ActorRef = context.actorOf(props, name)

  def getActor(name: String) = context.child(name)
}