package io.vamp.persistence

import io.vamp.common.Config
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object KeyValueStoreActor {

  val timeout = PersistenceActor.timeout

  sealed trait KeyValueStoreMessage{def path: KeyValueStorePath}

  case class All(path: KeyValueStorePath) extends KeyValueStoreMessage

  case class Get(path: KeyValueStorePath) extends KeyValueStoreMessage

  case class Set(path: KeyValueStorePath, data: Option[String]) extends KeyValueStoreMessage

}

trait KeyValueStoreActor extends NamespaceValueResolver with PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import KeyValueStoreActor._

  lazy implicit val timeout = KeyValueStoreActor.timeout()

  lazy val httpClient = new HttpClient

  lazy val basePath = resolveWithNamespace(Config.string("vamp.persistence.key-value-store.base-path")().stripMargin('/'))

  protected implicit val kvActor: KeyValueStoreActor = this

  def receive = {
    case InfoRequest     ⇒ reply(info)
    case All(path)       ⇒ reply(all(path))
    case Get(path)       ⇒ reply(get(path))
    case Set(path, data) ⇒ reply(set(path, data))
    case any             ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def info: Future[Any]

  protected def all(path: KeyValueStorePath): Future[List[String]]

  protected def get(path: KeyValueStorePath): Future[Option[String]]

  protected def set(path: KeyValueStorePath, data: Option[String]): Future[Any]

  override def typeName = "key-value"

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  override def errorNotificationClass = classOf[PersistenceOperationFailure]
}

case class KeyValueStorePath(elements: List[String]) {
  def toPathString(implicit kvActor: KeyValueStoreActor) =  s"/${(kvActor.basePath :: elements).mkString("/")}"
  def pathStringLength(implicit kvActor: KeyValueStoreActor) =  toPathString.length
  def :+ (el: String): KeyValueStorePath = KeyValueStorePath(elements :+ el)
}
