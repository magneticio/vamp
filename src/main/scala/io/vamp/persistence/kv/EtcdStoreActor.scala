package io.vamp.persistence.kv

import akka.http.scaladsl.model._
import io.vamp.common.config.Config

import scala.concurrent.Future

case class EtcdKeyValue(node: EtcdNode)

case class EtcdNode(key: Option[String] = None, value: Option[String] = None, nodes: List[EtcdNode] = Nil)

class EtcdStoreActor extends KeyValueStoreActor {

  private val url = Config.string("vamp.persistence.key-value-store.etcd.url")

  override protected def info(): Future[Any] = for {
    version ← restClient.get[Any](s"$url/version")
    self ← restClient.get[Any](s"$url/v2/stats/self")
    store ← restClient.get[Any](s"$url/v2/stats/store")
  } yield Map(
    "type" -> "etcd",
    "etcd" -> Map(
      "version" -> version,
      "statistics" -> self,
      "store" -> store
    )
  )

  override protected def all(path: List[String]): Future[List[String]] = {
    restClient.get[EtcdKeyValue](urlOf(path), logError = true) recover { case _ ⇒ EtcdKeyValue(EtcdNode()) } map {
      entry ⇒ entry.node.nodes.flatMap(node ⇒ node.key.map(_.substring(entry.node.key.getOrElse("").length + 1)))
    }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    restClient.get[Option[EtcdKeyValue]](urlOf(path), logError = false) recover { case _ ⇒ None } map {
      _.flatMap(_.node.value)
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ restClient.delete(urlOf(path), logError = false)
    case Some(value) ⇒ restClient.httpWithEntity[Any](HttpMethods.PUT, urlOf(path), Option(FormData("value" -> value).toEntity), logError = false)
  }

  private def urlOf(path: List[String], recursive: Boolean = false) = s"$url/v2/keys${KeyValueStoreActor.pathToString(path)}${if (recursive) "?recursive=true" else ""}"
}
