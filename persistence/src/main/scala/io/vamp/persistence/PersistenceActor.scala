package io.vamp.persistence

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.{ ErrorNotification, Notification, NotificationProvider }
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.common.{ Artifact, Config, ConfigMagnet }
import io.vamp.model.event.Event
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor extends DeploymentPersistenceMessages with GatewayPersistenceMessages with WorkflowPersistenceMessages {

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact], page: Int, perPage: Int, expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact], expandReferences: Boolean = false, onlyReferences: Boolean = false) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  val timeout: ConfigMagnet[Timeout] = Config.timeout("vamp.persistence.response-timeout")
}

trait PersistenceApi extends TypeOfArtifact {
  this: NotificationProvider ⇒

  protected def info(): Map[String, Any]

  protected def all[T <: Artifact](kind: String, page: Int, perPage: Int, filter: (T) ⇒ Boolean): ArtifactResponseEnvelope

  protected def get[T <: Artifact](name: String, kind: String): Option[T]

  protected def set[T <: Artifact](artifact: T, kind: String): T

  protected def delete[T <: Artifact](name: String, kind: String): Option[T]

  protected def interceptor[T <: Artifact](set: Boolean): PartialFunction[T, T] = PartialFunction(artifact ⇒ artifact)

  final protected def all[T <: Artifact](`type`: Class[T], page: Int, perPage: Int, filter: (T) ⇒ Boolean = (_: T) ⇒ true): ArtifactResponseEnvelope = {
    all[T](type2string(`type`), page, perPage, filter)
  }

  final protected def get[T <: Artifact](name: String, `type`: Class[T]): Option[T] = get[T](name, type2string(`type`))

  final protected def get[T <: Artifact](artifact: T): Option[T] = get[T](artifact.name, artifact.getClass.asInstanceOf[Class[T]])

  final protected def set[T <: Artifact](artifact: T): T = {
    val `type` = type2string(artifact.getClass)
    set[T](interceptor[T](set = true)(artifact), `type`)
  }

  final protected def delete[T <: Artifact](name: String, `type`: Class[T]): Boolean = {
    delete[T](name, type2string(`type`)).map(interceptor[T](set = false)).isDefined
  }

  final protected def delete[T <: Artifact](artifact: T): Boolean = delete[T](artifact.name, artifact.getClass.asInstanceOf[Class[T]])
}

trait PersistenceActor
    extends CommonPersistenceOperations
    with PatchPersistenceOperations
    with DeploymentPersistenceOperations
    with GatewayPersistenceOperations
    with WorkflowPersistenceOperations
    with PersistenceApi
    with PersistenceStats
    with PulseFailureNotifier
    with CommonSupportForActors
    with CommonProvider
    with PersistenceNotificationProvider {

  override val typeName = "persistence"

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  override def receive: Actor.Receive = {
    super[CommonPersistenceOperations].receive orElse
      super[DeploymentPersistenceOperations].receive orElse
      super[GatewayPersistenceOperations].receive orElse
      super[WorkflowPersistenceOperations].receive orElse {
        case InfoRequest  ⇒ reply(Future(Map("database" → info(), "archiving" → true)))
        case StatsRequest ⇒ reply(stats())
        case _: Event     ⇒
        case other        ⇒ unsupported(UnsupportedPersistenceRequest(other))
      }
  }

  final override protected def interceptor[T <: Artifact](set: Boolean): PartialFunction[T, T] = {
    super[DeploymentPersistenceOperations].interceptor[T](set) orElse
      super[GatewayPersistenceOperations].interceptor[T](set) orElse
      super[WorkflowPersistenceOperations].interceptor[T](set) orElse
      ({
        case artifact ⇒ artifact.asInstanceOf[T]
      }: PartialFunction[T, T])
  }

  override def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[PersistenceOperationFailure]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
