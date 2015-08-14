package io.vamp.core.persistence

import _root_.io.vamp.common.vitals.InfoRequest
import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.NotificationErrorException
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.persistence.notification._

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

  case class All(`type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class AllPaginated(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

trait PersistenceActor extends ArtifactExpansion with ArtifactShrinkage with ReplyActor with CommonSupportForActors with PersistenceNotificationProvider {

  import PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  protected def info(): Any

  protected def all(`type`: Class[_ <: Artifact]): List[Artifact]

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean): Artifact

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  protected def update(artifact: Artifact, source: Option[String], create: Boolean): Artifact

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Artifact

  final def reply(request: Any): Any = try {
    log.debug(s"${getClass.getSimpleName}: ${request.getClass.getSimpleName}")
    respond(request)
  } catch {
    case e: NotificationErrorException => e
    case e: Throwable => reportException(PersistenceOperationFailure(e))
  }

  protected def respond(request: Any) = request match {

    case Start => start()

    case Shutdown => shutdown()

    case InfoRequest => info()

    case All(ofType) => all(ofType)

    case AllPaginated(ofType, page, perPage, expandRef, onlyRef) => (expandRef, onlyRef) match {
      case (true, false) =>
        val artifacts = all(ofType, page, perPage)
        artifacts.copy(response = artifacts.response.map(expandReferences))

      case (false, true) =>
        val artifacts = all(ofType, page, perPage)
        artifacts.copy(response = artifacts.response.map(onlyReferences))

      case _ => all(ofType, page, perPage)
    }

    case Create(artifact, source, ignoreIfExists) => create(artifact, source, ignoreIfExists)

    case Read(name, ofType, expandRef, onlyRef) => (expandRef, onlyRef) match {
      case (true, false) => expandReferences(read(name, ofType))
      case (false, true) => onlyReferences(read(name, ofType))
      case _ => read(name, ofType)
    }

    case Update(artifact, source, create) => update(artifact, source, create)

    case Delete(name, ofType) => delete(name, ofType)

    case _ => throwException(errorRequest(request))
  }

  protected def start() = {}

  protected def shutdown() = {}
}

