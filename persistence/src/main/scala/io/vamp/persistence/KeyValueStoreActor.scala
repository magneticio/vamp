package io.vamp.persistence

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.common.vitals.InfoRequest
import io.vamp.common.{ CacheStore, Config, ConfigMagnet }
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, PersistenceOperationFailure, UnsupportedPersistenceRequest }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object KeyValueStoreActor {

  val timeout: ConfigMagnet[Timeout] = PersistenceActor.timeout

  sealed trait KeyValueStoreMessage

  case class Children(path: List[String]) extends KeyValueStoreMessage

  case class Get(path: List[String]) extends KeyValueStoreMessage

  case class Set(path: List[String], data: Option[String]) extends KeyValueStoreMessage

}

trait KeyValueStoreActor extends NamespaceValueResolver with PulseFailureNotifier with CommonSupportForActors with PersistenceNotificationProvider {

  import KeyValueStoreActor._

  lazy implicit val timeout: Timeout = KeyValueStoreActor.timeout()

  lazy val httpClient = new HttpClient

  override val typeName = "key-value"

  private val config = "vamp.persistence.key-value-store"

  private val cache = new CacheStore()

  private lazy val cacheTtl = Config.duration(s"$config.cache.read-ttl")()

  private lazy val basePath = resolveWithNamespace(Config.string(s"$config.base-path")().stripMargin('/'))

  def receive: Actor.Receive = receiveKeyValueStoreMessage orElse {
    case any ⇒ unsupported(UnsupportedPersistenceRequest(any))
  }

  protected def receiveKeyValueStoreMessage: Actor.Receive = {
    case InfoRequest     ⇒ reply(info())
    case Children(path)  ⇒ reply(children(path))
    case Get(path)       ⇒ reply(cacheGet(path))
    case Set(path, data) ⇒ reply(cacheSet(path, data))
  }
  override def postStop(): Unit = cache.close()

  protected def info(): Future[Any]

  protected def children(path: List[String]): Future[List[String]]

  protected def get(path: List[String]): Future[Option[String]]

  protected def set(path: List[String], data: Option[String]): Future[Any]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)

  override def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[PersistenceOperationFailure]

  protected def pathToString(path: List[String]) = s"/${(basePath :: path).mkString("/")}"

  private def cacheGet(path: List[String]): Future[Option[String]] = {
    val key = cacheId(path)
    cache.get[Future[Option[String]]](key) match {
      case Some(result) ⇒
        log.debug(s"cache get: $key")
        result
      case None ⇒
        log.debug(s"cache put [${cacheTtl.toSeconds} s]: $key")
        val value = get(path)
        cache.put(key, value, cacheTtl)
        value
    }
  }

  private def cacheSet(path: List[String], data: Option[String]): Future[Any] = set(path, data).map { result ⇒
    val key = cacheId(path)
    log.debug(s"cache put [${cacheTtl.toSeconds} s]: $key")
    cache.put(key, Future.successful(data), cacheTtl)
    result
  }

  private def cacheId(path: List[String]): String = path.mkString("/")
}
