package io.vamp.persistence.db

import akka.actor.Actor
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.{InfoRequest, StatsRequest}
import io.vamp.model.artifact._
import io.vamp.persistence.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor extends CommonPersistenceMessages with DevelopmentPersistenceMessages with GatewayPersistenceMessages with WorkflowPersistenceMessages {

  trait PersistenceMessages

  val timeout = Config.timeout("vamp.persistence.response-timeout")
}

trait PersistenceActor
    extends CommonPersistenceOperations
    with DevelopmentPersistenceOperations
    with GatewayPersistenceOperations
    with WorkflowPersistenceOperations
    with PersistenceStats
    with PulseFailureNotifier
    with CommonSupportForActors
    with PersistenceNotificationProvider {

  lazy implicit val timeout = PersistenceActor.timeout

  protected def info(): Future[Any]

  override def errorNotificationClass = classOf[PersistenceOperationFailure]

  def receive = receiveCommon orElse receiveDevelopment orElse receiveGateway orElse receiveWorkflow orElse receiveDefault

  private def receiveDefault: Actor.Receive = {
    case InfoRequest  ⇒ reply(info() map { persistenceInfo ⇒ Map("database" → persistenceInfo, "archiving" → true) })
    case StatsRequest ⇒ reply(stats())
    case other        ⇒ unsupported(UnsupportedPersistenceRequest(other))
  }

  override def typeName = "persistence"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}
