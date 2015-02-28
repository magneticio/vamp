package io.magnetic.vamp_core.persistence

import akka.actor.{Actor, ActorLogging, Props}
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ReplyActor, RequestError}
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.persistence.notification.{PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import io.magnetic.vamp_core.persistence.store.InMemoryArtifactStoreProvider


object ArtifactPersistenceActor extends ActorDescription {
  def props: Props = Props(new ArtifactPersistenceActor)
}

object ArtifactPersistenceMessages {

  trait ArtifactPersistenceMessages

  case class ReadAll(`type`: Class[Artifact]) extends ArtifactPersistenceMessages

  case class Create(artifact: Artifact) extends ArtifactPersistenceMessages

  case class Read(name: String, `type`: Class[Artifact]) extends ArtifactPersistenceMessages

  case class Update(artifact: Artifact) extends ArtifactPersistenceMessages

  case class Delete(name: String, `type`: Class[Artifact]) extends ArtifactPersistenceMessages

}

class ArtifactPersistenceActor extends Actor with ActorLogging with ReplyActor with InMemoryArtifactStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.persistence.ArtifactPersistenceMessages._

  override protected def requestType: Class[_] = classOf[ArtifactPersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply: Receive = {
    case ReadAll(t) => store.all(t)
    case Create(a) => store.create(a)
    case Read(n, t) => store.read(n, t)
    case Update(a) => store.update(a)
    case Delete(n, t) => store.delete(n, t)
    case _ =>
  }
}

