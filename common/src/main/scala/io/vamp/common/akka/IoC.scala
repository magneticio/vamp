package io.vamp.common.akka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._

import scala.collection.mutable
import scala.reflect._
import _root_.io.vamp.common.util.TextUtil

object IoC {

  private val counter = new AtomicInteger(0)

  private val aliases: mutable.Map[Class[_], Class[_]] = mutable.Map()

  private val actorRefs: mutable.Map[Class[_], ActorRef] = mutable.Map()

  def alias(from: Class[_]): Class[_] = aliases.getOrElse(from, from)

  def alias[FROM: ClassTag]: Class[_] = alias(classTag[FROM].runtimeClass)

  def alias(from: Class[_], to: Class[_]): Option[Class[_]] = aliases.put(from, to)

  def alias[FROM: ClassTag, TO: ClassTag]: Option[Class[_]] = alias(classTag[FROM].runtimeClass, classTag[TO].runtimeClass)

  def createActor(props: Props)(implicit actorSystem: ActorSystem): ActorRef = {

    val actorRef = actorSystem.actorOf(props, s"${TextUtil.toSnakeCase(props.clazz.getSimpleName)}-${counter.getAndIncrement}")

    actorRefs.put(props.clazz, actorRef)

    aliases.foreach {
      case (from, to) if to == props.clazz ⇒ actorRefs.put(from, actorRef)
      case _                               ⇒
    }

    actorRef
  }

  def createActor(clazz: Class[_])(implicit actorSystem: ActorSystem): ActorRef = createActor(Props(clazz))

  def createActor[ACTOR: ClassTag](implicit actorSystem: ActorSystem): ActorRef = createActor(classTag[ACTOR].runtimeClass)

  def createActor[ACTOR: ClassTag](arg: Any, args: Any*)(implicit actorSystem: ActorSystem): ActorRef = createActor(Props(classTag[ACTOR].runtimeClass, arg :: args.toList: _*))

  def actorFor(clazz: Class[_])(implicit actorSystem: ActorSystem): ActorRef = actorRefs.get(alias(clazz)) match {
    case Some(actorRef) ⇒ actorRef
    case _              ⇒ throw new RuntimeException(s"No actor reference for: $clazz")
  }

  def actorFor[ACTOR: ClassTag](implicit actorSystem: ActorSystem): ActorRef = actorFor(classTag[ACTOR].runtimeClass)
}

