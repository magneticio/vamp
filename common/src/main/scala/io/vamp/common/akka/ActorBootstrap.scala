package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import io.vamp.common.spi.ClassProvider

import scala.reflect.{ ClassTag, classTag }

trait Bootstrap {

  def run(): Unit = {}

  def shutdown(): Unit = {}
}

trait ActorBootstrap {

  private var actors: List[ActorRef] = Nil

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef]

  def run(implicit actorSystem: ActorSystem): Unit = {
    actors = createActors(actorSystem)
  }

  def shutdown(implicit actorSystem: ActorSystem): Unit = actors.reverse.foreach {
    _ ! PoisonPill
  }

  def alias[T: ClassTag](name: String, default: String ⇒ ActorRef)(implicit actorSystem: ActorSystem): ActorRef = {
    ClassProvider.find[T](name).map { clazz ⇒
      IoC.alias(classTag[T].runtimeClass, clazz)
      IoC.createActor(clazz)
    } getOrElse default(name)
  }
}
