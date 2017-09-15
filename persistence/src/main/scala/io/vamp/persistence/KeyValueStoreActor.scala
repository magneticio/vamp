package io.vamp.persistence

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object KeyValueStoreActor {

  val timeout: ConfigMagnet[Timeout] = PersistenceActor.timeout

  sealed trait KeyValueStoreMessage

  case class All(path: List[String]) extends KeyValueStoreMessage

  case class Get(path: List[String]) extends KeyValueStoreMessage

  case class Set(path: List[String], data: Option[String]) extends KeyValueStoreMessage

}

trait KeyValueStoreActor extends NamespaceValueResolver with PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import KeyValueStoreActor._

  lazy implicit val timeout: Timeout = KeyValueStoreActor.timeout()

  lazy val httpClient = new HttpClient

  private lazy val basePath = resolveWithNamespace(Config.string("vamp.persistence.key-value-store.base-path")().stripMargin('/'))

  def receive: Actor.Receive = {
    case InfoRequest     ⇒ reply(info())
    case All(path)       ⇒ reply(all(path))
    case Get(path)       ⇒ reply(get(path))
    case Set(path, data) ⇒ reply(set(path, data))
    case any             ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def info(): Future[Any]

  protected def all(path: List[String]): Future[List[String]]

  protected def get(path: List[String]): Future[Option[String]]

  protected def set(path: List[String], data: Option[String]): Future[Any]

  override def typeName = "key-value"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)

  override def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[PersistenceOperationFailure]

  protected def pathToString(path: List[String]) = s"/${(basePath :: path).mkString("/")}"
}
