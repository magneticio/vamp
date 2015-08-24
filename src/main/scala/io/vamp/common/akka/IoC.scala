package io.vamp.common.akka

import akka.actor._

import scala.collection.mutable

object IoC {

  private val aliases: mutable.Map[String, ActorDescription] = mutable.Map()

  def alias(from: ActorDescription) = aliases.getOrElse(from.name, from)

  def alias(from: ActorDescription, to: ActorDescription) = aliases.put(from.name, to)

  def createActor(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox", actorSystem: ActorSystem) = {
    val description = alias(actorDescription)
    actorSystem.actorOf(description.props(args: _*).withMailbox(mailbox), description.name)
  }

  def actorFor(actorDescription: ActorDescription)(implicit actorSystem: ActorSystem) = {
    actorSystem.actorSelection(s"/user/${alias(actorDescription).name}")
  }
}
