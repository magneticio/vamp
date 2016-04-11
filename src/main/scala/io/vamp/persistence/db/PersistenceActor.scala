package io.vamp.persistence.db

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.model.artifact._
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.persistence.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials
import scala.reflect._

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor {

  val timeout = Timeout(ConfigFactory.load().getInt("vamp.persistence.response-timeout").seconds)

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

trait PersistenceActor extends PersistenceMultiplexer with PersistenceArchive with PersistenceStats with ArtifactExpansion with ArtifactShrinkage with PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  protected def info(): Future[Any]

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope]

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

  protected def set(artifact: Artifact): Future[Artifact]

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean]

  override def errorNotificationClass = classOf[PersistenceOperationFailure]

  def receive = {

    case InfoRequest ⇒ reply {
      info() flatMap {
        case persistenceInfo ⇒ IoC.actorFor[KeyValueStoreActor] ? InfoRequest map {
          case keyValueInfo ⇒ Map("database" -> persistenceInfo, "keyValue" -> keyValueInfo, "archive" -> true)
        }
      }
    }

    case StatsRequest ⇒ reply {
      stats()
    }

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ reply {
      all(ofType, page, perPage).flatMap(combine).flatMap {
        case artifacts ⇒ (expandRef, onlyRef) match {
          case (true, false) ⇒ Future.sequence(artifacts.response.map(expandReferences)).map { case response ⇒ artifacts.copy(response = response) }
          case (false, true) ⇒ Future.successful(artifacts.copy(response = artifacts.response.map(onlyReferences)))
          case _             ⇒ Future.successful(artifacts)
        }
      }
    }

    case Read(name, ofType, expandRef, onlyRef) ⇒ reply {
      get(name, ofType).flatMap(combine).flatMap {
        case artifact ⇒
          (expandRef, onlyRef) match {
            case (true, false) ⇒ expandReferences(artifact)
            case (false, true) ⇒ Future.successful(onlyReferences(artifact))
            case _             ⇒ Future.successful(artifact)
          }
      }
    }

    case Create(artifact, source) ⇒ reply {
      split(artifact, { artifact: Artifact ⇒
        set(artifact) map {
          archiveCreate(_, source)
        }
      })
    }

    case Update(artifact, source) ⇒ reply {
      split(artifact, { artifact: Artifact ⇒
        set(artifact) map {
          archiveUpdate(_, source)
        }
      })
    }

    case Delete(name, ofType) ⇒ reply {
      remove(name, ofType, { (name, ofType) ⇒
        delete(name, ofType) map {
          result ⇒
            if (result) archiveDelete(name, ofType)
            result
        }
      })
    }

    case any ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def readExpanded[T <: Artifact: ClassTag](name: String): Future[Option[T]] = {
    get(name, classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]).asInstanceOf[Future[Option[T]]]
  }

  override def typeName = "persistence"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

