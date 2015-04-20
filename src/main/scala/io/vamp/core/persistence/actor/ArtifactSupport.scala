package io.vamp.core.persistence.actor

import _root_.io.vamp.common.akka.{ActorSupport, FutureSupport}
import _root_.io.vamp.common.notification.NotificationProvider
import _root_.io.vamp.core.model.artifact.Artifact
import _root_.io.vamp.core.persistence.notification.ArtifactNotFound
import akka.pattern.ask

import scala.reflect._

trait ArtifactSupport {
  this: FutureSupport with ActorSupport with NotificationProvider =>

  def artifactFor[T <: Artifact : ClassTag](artifact: Option[Artifact]): Option[T] = artifact match {
    case None => None
    case Some(a) => Some(artifactFor[T](a))
  }

  def artifactFor[T <: Artifact : ClassTag](artifact: Artifact): T = artifact match {
    case a: T => a
    case _ => artifactFor[T](artifact.name)
  }

  def artifactFor[T <: Artifact : ClassTag](name: String): T = {
    implicit val timeout = PersistenceActor.timeout
    offload(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]])) match {
      case Some(artifact: T) => artifact
      case _ => error(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }
}
