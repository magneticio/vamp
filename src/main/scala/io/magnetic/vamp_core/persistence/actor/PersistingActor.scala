package io.magnetic.vamp_core.persistence.actor

import akka.actor.{Actor, ActorLogging}
import io.magnetic.vamp_common.akka.{ActorExecutionContextProvider, FutureSupport, ReplyActor, RequestError}
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.persistence.actor.PersistenceActor._
import io.magnetic.vamp_core.persistence.notification.{PersistenceNotificationProvider, UnsupportedPersistenceRequest}

import scala.concurrent.Future

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