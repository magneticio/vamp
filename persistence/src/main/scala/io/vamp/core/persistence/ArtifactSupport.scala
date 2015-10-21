package io.vamp.core.persistence

import akka.pattern.ask
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.persistence.notification.ArtifactNotFound

import scala.concurrent.Future
import scala.reflect._

trait ArtifactSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def artifactFor[T <: Artifact: ClassTag](artifact: Option[Artifact]): Future[Option[T]] = artifact match {
    case None    ⇒ Future(None)
    case Some(a) ⇒ artifactFor[T](a).map(Some(_))
  }

  def artifactFor[T <: Artifact: ClassTag](artifact: Artifact): Future[T] = artifact match {
    case a: T ⇒ Future(a)
    case _    ⇒ artifactFor[T](artifact.name)
  }

  def artifactFor[T <: Artifact: ClassTag](name: String): Future[T] = {
    implicit val timeout = PersistenceActor.timeout
    IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]]) map {
      case Some(artifact: T) ⇒ artifact
      case _                 ⇒ throwException(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }
}
