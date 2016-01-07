package io.vamp.persistence.kv

import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object KeyValueStoreActor {

  val timeout = PersistenceActor.timeout

  sealed trait KeyValueStoreMessage

  case class Get(path: List[String]) extends KeyValueStoreMessage

  case class Set(path: List[String], data: Option[String]) extends KeyValueStoreMessage

}

trait KeyValueStoreActor extends PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import KeyValueStoreActor._

  lazy implicit val timeout = KeyValueStoreActor.timeout

  def receive = {
    case InfoRequest     ⇒ reply { info() }
    case Get(path)       ⇒ reply { get(path) }
    case Set(path, data) ⇒ set(path, data)
    case any             ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def info(): Future[Any]

  protected def get(path: List[String]): Future[Option[String]]

  protected def set(path: List[String], data: Option[String]): Unit

  override def typeName = "key-value"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  override def errorNotificationClass = classOf[PersistenceOperationFailure]
}
