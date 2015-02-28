package io.magnetic.vamp_common.akka

import akka.actor._
import io.magnetic.vamp_common.text.Text

trait ActorDescription {
  def props: Props

  def name: String = Text.toSnakeCase(getClass.getSimpleName)
}

object ActorSupport {
  def actorOf(ad: ActorDescription)(implicit actorSystem: ActorSystem) = actorSystem.actorOf(ad.props, ad.name)
}

trait ActorSupport {
  this: Actor =>

  //def createActor(props: Props, name: String): ActorRef = context.actorOf(props, name)

  def actorOf(ad: ActorDescription) = context.system.actorOf(ad.props, ad.name)

  def actorFor(name: String) = context.actorSelection(s"../$name")

  //def getActor(name: String) = context.child(name)

  //def getTopLevelActor(name: String) = context.system.child(name)

  //def getOrCreateActor(props: Props, name: String): ActorRef = getActor(name).getOrElse(createActor(props, name))

  //def getOrCreateTopLevelActor(props: Props, name: String): ActorRef = getTopLevelActor(name).getOrElse(createTopLevelActor(props, name))

  //def actorFor(name: String): ActorSelection = context.actorSelection(self.path.root / "user" / name)
}