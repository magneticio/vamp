package io.vamp.persistence

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.artifact.Artifact
import io.vamp.persistence.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.persistence.response-timeout").seconds)

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

trait PersistenceActor extends PersistenceArchiving with ArtifactExpansion with ArtifactShrinkage with PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  protected def info(): Future[Any]

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope

  protected def create(artifact: Artifact, ignoreIfExists: Boolean): Artifact

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  protected def update(artifact: Artifact, create: Boolean): Artifact

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  override def errorNotificationClass = classOf[PersistenceOperationFailure]

  def receive = {

    case Start    ⇒ start()

    case Shutdown ⇒ shutdown()

    case InfoRequest ⇒ reply {
      info() map {
        case map: Map[_, _] ⇒ map.asInstanceOf[Map[Any, Any]] ++ Map("archive" -> true)
        case other          ⇒ other
      }
    }

    case All(ofType, page, perPage, expandRef, onlyRef) ⇒ try {
      val artifacts = all(ofType, page, perPage)
      sender() ! ((expandRef, onlyRef) match {
        case (true, false) ⇒ artifacts.copy(response = artifacts.response.map(expandReferences))
        case (false, true) ⇒ artifacts.copy(response = artifacts.response.map(onlyReferences))
        case _             ⇒ artifacts
      })
    } catch {
      case e: Throwable ⇒ sender() ! failure(e)
    }

    case Create(artifact, source, ignoreIfExists) ⇒ try {
      sender() ! archiveCreate(create(artifact, ignoreIfExists), source)
    } catch {
      case e: Throwable ⇒ sender() ! failure(e)
    }

    case Read(name, ofType, expandRef, onlyRef) ⇒ try {
      val artifact = read(name, ofType)
      sender() ! ((expandRef, onlyRef) match {
        case (true, false) ⇒ expandReferences(artifact)
        case (false, true) ⇒ onlyReferences(artifact)
        case _             ⇒ artifact
      })
    } catch {
      case e: Throwable ⇒ sender() ! failure(e)
    }

    case Update(artifact, source, create) ⇒ try {
      sender() ! archiveUpdate(update(artifact, create), source)
    } catch {
      case e: Throwable ⇒ sender() ! failure(e)
    }

    case Delete(name, ofType) ⇒ try {
      sender() ! archiveDelete(delete(name, ofType))
    } catch {
      case e: Throwable ⇒ sender() ! failure(e)
    }

    case any ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def start() = {
  }

  protected def shutdown() = {
  }

  protected def readExpanded(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = read(name, `type`)

  override def typeName = "persistence"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

