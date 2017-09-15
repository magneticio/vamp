package io.vamp.persistence

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.{ Artifact, Config, ConfigMagnet }
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor extends CommonPersistenceMessages with DeploymentPersistenceMessages with GatewayPersistenceMessages with WorkflowPersistenceMessages {

  trait PersistenceMessages

  val timeout: ConfigMagnet[Timeout] = Config.timeout("vamp.persistence.response-timeout")
}

trait PersistenceActor
    extends CommonPersistenceOperations
    with DeploymentPersistenceOperations
    with GatewayPersistenceOperations
    with WorkflowPersistenceOperations
    with PersistenceStats
    with PulseFailureNotifier
    with CommonSupportForActors
    with PersistenceNotificationProvider {

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  protected def info(): Future[Any]

  override def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[PersistenceOperationFailure]

  override def receive: Actor.Receive = {
    super[CommonPersistenceOperations].receive orElse
      super[DeploymentPersistenceOperations].receive orElse
      super[GatewayPersistenceOperations].receive orElse
      super[WorkflowPersistenceOperations].receive orElse {
        case InfoRequest  ⇒ reply(info() map { persistenceInfo ⇒ Map("database" → persistenceInfo, "archiving" → true) })
        case StatsRequest ⇒ reply(stats())
        case other        ⇒ unsupported(UnsupportedPersistenceRequest(other))
      }
  }

  override def typeName = "persistence"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
