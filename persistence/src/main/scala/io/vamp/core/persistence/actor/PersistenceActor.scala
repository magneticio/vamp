package io.vamp.core.persistence.actor

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.core.model.artifact.Artifact
import io.vamp.common.akka._
import io.vamp.core.persistence.actor.PersistenceActor.PersistenceMessages
import io.vamp.core.persistence.notification.UnsupportedPersistenceRequest
import io.vamp.core.persistence.store.InMemoryStoreProvider

import scala.concurrent.duration._
import scala.language.existentials

object PersistenceActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("persistence.response.timeout").seconds)

  def props(args: Any*): Props = Props[PersistenceActor]

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class Create(artifact: Artifact, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class Update(artifact: Artifact, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

class PersistenceActor extends PersistingActor with InMemoryStoreProvider {

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  override def createDefaultArtifact(artifact: Artifact, ignoreIfExists: Boolean): Artifact = store.create(artifact, ignoreIfExists)

  override def getAllDefaultArtifacts(ofType: Class[_ <: Artifact]): List[_ <: Artifact] = store.all(ofType)

  override def updateDefaultArtifact(artifact: Artifact, create: Boolean): Artifact = store.update(artifact, create)

  override def deleteDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Artifact = store.delete(name, ofType)

  override def readDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = store.read(name, ofType)
}
