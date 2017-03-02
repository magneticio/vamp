package io.vamp.persistence

import akka.pattern.ask
import io.vamp.common.akka.{ CommonProvider, IoC }
import io.vamp.persistence.notification.{ ArtifactNotFound, PersistenceOperationFailure }

import scala.concurrent.Future
import scala.reflect._
import _root_.io.vamp.common.Artifact

trait ArtifactSupport {
  this: CommonProvider ⇒

  def artifactFor[T <: Artifact: ClassTag](artifact: Option[Artifact]): Future[Option[T]] = artifact match {
    case None    ⇒ Future.successful(None)
    case Some(a) ⇒ artifactFor[T](a).map(Some(_))
  }

  def artifactFor[T <: Artifact: ClassTag](artifact: Artifact, force: Boolean = false): Future[T] = artifact match {
    case a: T if !force ⇒ Future.successful(a)
    case _              ⇒ artifactFor[T](artifact.name)
  }

  def artifactFor[T <: Artifact: ClassTag](name: String): Future[T] = {
    artifactForIfExists[T](name) map {
      case Some(artifact: T) ⇒ artifact
      case _                 ⇒ throwException(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }

  def artifactForIfExists[T <: Artifact: ClassTag](name: String): Future[Option[T]] = {
    implicit val timeout = PersistenceActor.timeout()
    IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]]) map {
      case Some(artifact: T) ⇒ Some(artifact)
      case Some(artifact)    ⇒ throwException(PersistenceOperationFailure(new RuntimeException(s"Expected: [${classTag[T].runtimeClass}], actual: [${artifact.getClass}]")))
      case None              ⇒ None
    }
  }
}
