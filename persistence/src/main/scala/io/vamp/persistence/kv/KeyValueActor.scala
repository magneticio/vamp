package io.vamp.persistence.kv

import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

trait KeyValueActor extends PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  lazy implicit val timeout = PersistenceActor.timeout

  def receive = {

    case InfoRequest ⇒ reply { Future.successful("key-value") }

    case any         ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  override def typeName = "key-value"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  override def errorNotificationClass = classOf[PersistenceOperationFailure]
}

