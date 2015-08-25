package io.vamp.common.akka

import akka.actor._

import scala.collection.mutable

object IoC {

  private val aliases: mutable.Map[String, ActorDescription] = mutable.Map()

  private val actorRefs: mutable.Map[String, ActorRef] = mutable.Map()

  def alias(from: ActorDescription) = aliases.getOrElse(from.name, from)

  def alias(from: ActorDescription, to: ActorDescription) = aliases.put(from.name, to)

  def createActor(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox", actorSystem: ActorSystem): ActorRef = {

    val description = alias(actorDescription)
    val actorRef = actorSystem.actorOf(description.props(args: _*).withMailbox(mailbox), description.name)

    actorRefs.put(description.name, actorRef)
    actorRef
  }

  def actorFor(actorDescription: ActorDescription)(implicit actorSystem: ActorSystem): ActorRef = {

    val description = alias(actorDescription)

    actorRefs.get(description.name) match {
      case Some(actorRef) => actorRef
      case _ => throw new RuntimeException(s"No actor reference for: ${actorDescription.name}")
    }
  }
}
