package io.vamp.common.akka

import akka.actor._
import io.vamp.common.text.Text

import scala.collection.mutable

trait ActorDescription {
  def props(args: Any*): Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {

  private val aliases: mutable.Map[String, ActorDescription] = mutable.Map()

  def alias(from: ActorDescription) = aliases.getOrElse(from.name, from)

  def alias(from: ActorDescription, to: ActorDescription) = aliases.put(from.name, to)

  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox", actorRefFactory: ActorRefFactory) = {
    val description = aliases.getOrElse(actorDescription.name, actorDescription)
    actorRefFactory.actorOf(description.props(args: _*).withMailbox(mailbox), description.name)
  }

  def actorFor(actorDescription: ActorDescription)(implicit actorRefFactory: ActorRefFactory) = {
    val description = aliases.getOrElse(actorDescription.name, actorDescription)
    actorRefFactory.actorSelection(s"/user/${description.name}")
  }
}

trait ActorSupport {

  implicit def actorRefFactory: ActorRefFactory

  def actorOf(actorDescription: ActorDescription, args: Any*)(implicit mailbox: String = "akka.actor.default-mailbox") = ActorSupport.actorOf(actorDescription, args)

  def actorFor(actorDescription: ActorDescription) = ActorSupport.actorFor(actorDescription)
}

trait ActorSupportForActors extends ActorSupport {
  this: Actor =>

  def actorRefFactory = context
}