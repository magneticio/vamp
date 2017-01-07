package io.vamp.persistence.db

import io.vamp.persistence.notification.{ ArtifactNotFound, PersistenceOperationFailure }
import akka.pattern.ask
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Artifact

import scala.concurrent.Future
import scala.reflect._

trait ArtifactSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def artifactFor[T <: Artifact: ClassTag](artifact: Option[Artifact]): Future[Option[T]] = artifact match {
    case None    ⇒ Future.successful(None)
    case Some(a) ⇒ artifactFor[T](a).map(Some(_))
  }

  def artifactFor[T <: Artifact: ClassTag](artifact: Artifact): Future[T] = artifact match {
    case a: T ⇒ Future.successful(a)
    case _    ⇒ artifactFor[T](artifact.name)
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
