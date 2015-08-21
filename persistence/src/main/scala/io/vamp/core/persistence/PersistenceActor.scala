package io.vamp.core.persistence

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.persistence.notification._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor extends ActorDescription {

  def props(args: Any*): Props = Props.empty

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.persistence.response-timeout").seconds)

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

trait PersistenceActor extends ArtifactExpansion with ArtifactShrinkage with ReplyActor with CommonSupportForActors with PersistenceNotificationProvider {

  import PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  protected def info(): Future[Any]

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope]

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean): Future[Artifact]

  protected def read(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

  protected def update(artifact: Artifact, source: Option[String], create: Boolean): Future[Artifact]

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

  override def errorNotificationClass = classOf[PersistenceOperationFailure]

  def receive = {

    case Start => start()

    case Shutdown => shutdown()

    case InfoRequest => reply {
      info()
    }

    case All(ofType, page, perPage, expandRef, onlyRef) => reply {
      all(ofType, page, perPage) map { case artifacts =>
        (expandRef, onlyRef) match {
          case (true, false) => artifacts.copy(response = artifacts.response.map(expandReferences))
          case (false, true) => artifacts.copy(response = artifacts.response.map(onlyReferences))
          case _ => artifacts
        }
      }
    }

    case Create(artifact, source, ignoreIfExists) => reply {
      create(artifact, source, ignoreIfExists)
    }

    case Read(name, ofType, expandRef, onlyRef) => reply {
      read(name, ofType) map { case artifact =>
        (expandRef, onlyRef) match {
          case (true, false) => expandReferences(artifact)
          case (false, true) => onlyReferences(artifact)
          case _ => read(name, ofType)
        }
      }
    }

    case Update(artifact, source, create) => reply {
      update(artifact, source, create)
    }

    case Delete(name, ofType) => reply {
      delete(name, ofType)
    }

    case any => unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def start() = {}

  protected def shutdown() = {}

  protected def readExpanded(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = None
}

