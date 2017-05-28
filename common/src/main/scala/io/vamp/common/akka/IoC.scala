package io.vamp.common.akka

import java.util.concurrent.atomic.AtomicInteger

import _root_.akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.util.TextUtil

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect._

object IoC {

  private val counter = new AtomicInteger(0)

  private val aliases: mutable.Map[String, mutable.Map[Class[_], Class[_]]] = mutable.Map()

  private val actorRefs: mutable.Map[String, mutable.Map[Class[_], ActorRef]] = mutable.Map()

  private val namespaceMap: mutable.Map[String, Namespace] = mutable.Map()

  private val namespaceActors: mutable.Map[String, ActorRef] = mutable.Map()

  def namespaces: List[Namespace] = namespaceMap.values.toList

  def alias[FROM: ClassTag](implicit namespace: Namespace): Class[_] = {
    alias(classTag[FROM].runtimeClass)
  }

  def alias(from: Class[_])(implicit namespace: Namespace): Class[_] = {
    aliases.get(namespace.name).flatMap(_.get(from)).getOrElse(from)
  }

  def alias[FROM: ClassTag, TO: ClassTag](implicit namespace: Namespace): Option[Class[_]] = {
    alias(classTag[FROM].runtimeClass, classTag[TO].runtimeClass)
  }

  def alias(from: Class[_], to: Class[_])(implicit namespace: Namespace): Option[Class[_]] = {
    aliases.getOrElseUpdate(namespace.name, mutable.Map()).put(from, to)
  }

  def createActor(clazz: Class[_])(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[ActorRef] = {
    createActor(Props(clazz))
  }

  def createActor[ACTOR: ClassTag](implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[ActorRef] = {
    createActor(classTag[ACTOR].runtimeClass)
  }

  def createActor[ACTOR: ClassTag](arg: Any, args: Any*)(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[ActorRef] = {
    createActor(Props(classTag[ACTOR].runtimeClass, arg :: args.toList: _*))
  }

  def createActor(props: Props)(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[ActorRef] = {
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    (namespaceActor ? props) map {
      case actorRef: ActorRef ⇒
        actorRefs.getOrElseUpdate(namespace.name, mutable.Map()).put(props.clazz, actorRef)
        aliases.getOrElseUpdate(namespace.name, mutable.Map()).foreach {
          case (from, to) if to == props.clazz ⇒ actorRefs.getOrElseUpdate(namespace.name, mutable.Map()).put(from, actorRef)
          case _                               ⇒
        }
        actorRef
      case _ ⇒ throw new RuntimeException(s"Cannot create actor for: ${props.clazz.getSimpleName}")
    }
  }

  def actorFor[ACTOR: ClassTag](implicit actorSystem: ActorSystem, namespace: Namespace): ActorRef = {
    actorFor(classTag[ACTOR].runtimeClass)
  }

  def actorFor(clazz: Class[_])(implicit actorSystem: ActorSystem, namespace: Namespace): ActorRef = {
    actorRefs.get(namespace.name).flatMap(_.get(alias(clazz))) match {
      case Some(actorRef) ⇒ actorRef
      case _              ⇒ throw new RuntimeException(s"No actor reference for: $clazz")
    }
  }

  private def namespaceActor(implicit actorSystem: ActorSystem, namespace: Namespace): ActorRef = {
    namespaceMap.put(namespace.name, namespace)
    namespaceActors.getOrElseUpdate(namespace.name, actorSystem.actorOf(Props(new Actor {
      def receive = {
        case props: Props ⇒ sender() ! context.actorOf(props, s"${TextUtil.toSnakeCase(props.clazz.getSimpleName)}-${counter.getAndIncrement}")
        case _            ⇒
      }
    }), namespace.name))
  }
}

