package io.magnetic.vamp_core.persistence.actor

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_common.notification.NotificationProvider
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.persistence.actor.PersistenceActor._
import _root_.io.magnetic.vamp_core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import akka.actor.{Actor, ActorLogging}
import akka.pattern.ask

import scala.concurrent.Future
import scala.reflect._

/**
 * Framework for the Persistence Actor
 */
trait PersistingActor extends Actor with ActorLogging with ReplyActor with FutureSupport with ActorExecutionContextProvider with PersistenceNotificationProvider {


  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply(request: Any) = {
    val future: Future[Any] = request match {
      case All(ofType) => Future {
        getAllDefaultArtifacts(ofType)
      }

      case Create(artifact, ignoreIfExists) => Future {
        createDefaultArtifact(artifact, ignoreIfExists)
      }

      case Read(name, ofType) => Future {
        readDefaultArtifact(name, ofType)
      }

      case Update(artifact, create) => Future {
        updateDefaultArtifact(artifact, create)
      }

      case Delete(name, ofType) => Future {
        deleteDefaultArtifact(name, ofType)
      }

      case _ => error(errorRequest(request))
    }

    offLoad(future)
  }

  def createDefaultArtifact(artifact: Artifact, ignoreIfExists: Boolean): Artifact

  def updateDefaultArtifact(artifact: Artifact, create: Boolean): Artifact

  def readDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact]

  def deleteDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Artifact

  def getAllDefaultArtifacts(ofType: Class[_ <: Artifact]): List[_ <: Artifact]

}

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
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]])) match {
      case Some(artifact: T) => artifact
      case _ => error(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }
}