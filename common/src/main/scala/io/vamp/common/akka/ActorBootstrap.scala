package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import com.typesafe.scalalogging.Logger
import io.vamp.common.{ ClassProvider, NamespaceResolver }
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.reflect.{ ClassTag, classTag }

trait Bootstrap {

  def start(): Unit = {}

  def stop(): Unit = {}
}

trait ActorBootstrap {

  protected val logger = Logger(LoggerFactory.getLogger(getClass))

  private var actors: List[ActorRef] = Nil

  implicit val namespaceResolver = new NamespaceResolver {
    val namespace: String = "default"
  }

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef]

  def start(implicit actorSystem: ActorSystem): Unit = {
    logger.info(s"Starting ${getClass.getSimpleName}")
    actors = createActors(actorSystem)
  }

  def restart(implicit actorSystem: ActorSystem): Unit = {
    implicit val executionContext = actorSystem.dispatcher
    stop.map(_ ⇒ start)
  }

  def stop(implicit actorSystem: ActorSystem): Future[Unit] = Future.successful {
    logger.info(s"Stopping ${getClass.getSimpleName}")
    actors.reverse.foreach(_ ! PoisonPill)
  }

  def alias[T: ClassTag](name: String, default: String ⇒ ActorRef)(implicit actorSystem: ActorSystem, namespaceResolver: NamespaceResolver): ActorRef = {
    ClassProvider.find[T](name).map { clazz ⇒
      IoC.alias(classTag[T].runtimeClass, clazz)
      IoC.createActor(clazz)
    } getOrElse default(name)
  }
}
