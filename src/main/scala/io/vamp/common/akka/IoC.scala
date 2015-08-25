package io.vamp.common.akka

import akka.actor._
import io.vamp.common.text.Text

import scala.collection.mutable
import scala.reflect._

object IoC {

  private val aliases: mutable.Map[Class[_], Class[_]] = mutable.Map()

  private val actorRefs: mutable.Map[Class[_], ActorRef] = mutable.Map()


  def alias[FROM: ClassTag] = aliases.getOrElse(classTag[FROM].runtimeClass, classTag[FROM].runtimeClass)

  def alias[FROM: ClassTag, TO: ClassTag] = aliases.put(classTag[FROM].runtimeClass, classTag[TO].runtimeClass)


  def createActor(props: Props)(implicit actorSystem: ActorSystem): ActorRef = {

    val actorRef = actorSystem.actorOf(props, Text.toSnakeCase(props.clazz.getSimpleName))

    actorRefs.put(props.clazz, actorRef)

    aliases.foreach {
      case (from, to) if to == props.clazz => actorRefs.put(from, actorRef)
      case _ =>
    }

    actorRef
  }

  def actorFor[T: ClassTag](implicit actorSystem: ActorSystem): ActorRef = actorRefs.get(alias[T]) match {
    case Some(actorRef) => actorRef
    case _ => throw new RuntimeException(s"No actor reference for: ${classTag[T].runtimeClass}")
  }
}

