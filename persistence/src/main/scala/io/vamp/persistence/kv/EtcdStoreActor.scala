package io.vamp.persistence.kv

import akka.http.scaladsl.model._
import io.vamp.common.config.Config

import scala.concurrent.Future

case class EtcdKeyValue(node: EtcdNode)

case class EtcdNode(key: Option[String] = None, value: Option[String] = None, nodes: List[EtcdNode] = Nil)

class EtcdStoreActor extends KeyValueStoreActor {

  private val url = Config.string("vamp.persistence.key-value-store.etcd.url")

  private val valueNode = "value"

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

    val key = KeyValueStoreActor.pathToString(path)

    def collect(childPath: List[String]): Future[List[String]] = {
      restClient.get[EtcdKeyValue](urlOf(childPath), logError = false).recover { case _ ⇒ EtcdKeyValue(EtcdNode()) } flatMap { entry ⇒
        entry.node.value match {
          case Some(_) ⇒
            Future.successful {
              val entryKey = entry.node.key.get
              entryKey.substring(key.length + 1, entryKey.length - valueNode.length - 1) :: Nil
            }
          case _ ⇒
            Future.sequence {
              entry.node.nodes.flatMap(_.key.map(_.substring(entry.node.key.getOrElse("").length + 1))).map(child ⇒ collect(childPath :+ child))
            }.map(_.flatten)
        }
      }
    }

    collect(path)
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    restClient.get[Option[EtcdKeyValue]](urlOfValue(path), logError = false).recover { case _ ⇒ None } map {
      _.flatMap(_.node.value)
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ restClient.delete(urlOfValue(path), logError = false).recover { case _ ⇒ false }
    case Some(value) ⇒ restClient.httpWithEntity[Any](HttpMethods.PUT, urlOfValue(path), Option(FormData("value" -> value).toEntity), logError = false)
  }

  private def urlOfValue(path: List[String]) = s"${urlOf(path, recursive = false)}/$valueNode"

  private def urlOf(path: List[String], recursive: Boolean = false) = s"$url/v2/keys${KeyValueStoreActor.pathToString(path)}${if (recursive) "?recursive=true" else ""}"
}
