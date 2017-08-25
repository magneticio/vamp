package io.vamp.persistence.etcd

import akka.http.scaladsl.model._
import io.vamp.common.{ClassMapper, Config}
import io.vamp.persistence.{KeyValueStoreActor, KeyValueStorePath}

import scala.concurrent.Future

case class EtcdKeyValue(node: EtcdNode)

case class EtcdNode(key: Option[String] = None, value: Option[String] = None, nodes: List[EtcdNode] = Nil)

class EtcdStoreActorMapper extends ClassMapper {
  val name = "etcd"
  val clazz = classOf[EtcdStoreActor]
}

class EtcdStoreActor extends KeyValueStoreActor {

  private lazy val url = Config.string("vamp.persistence.key-value-store.etcd.url")()

  private val valueNode = "value"

  override protected def info: Future[Any] = for {
    version ← httpClient.get[Any](s"$url/version")
    self ← httpClient.get[Any](s"$url/v2/stats/self")
    store ← httpClient.get[Any](s"$url/v2/stats/store")
  } yield Map(
    "type" → "etcd",
    "etcd" → Map(
      "version" → version,
      "statistics" → self,
      "store" → store
    )
  )

  override protected def all(path: KeyValueStorePath): Future[List[String]] = {

    def collect(childPath: KeyValueStorePath): Future[List[String]] = {
      httpClient.get[EtcdKeyValue](urlOf(childPath), logError = false).recover { case _ ⇒ EtcdKeyValue(EtcdNode()) } flatMap { entry ⇒
        entry.node.value match {
          case Some(_) ⇒
            Future.successful {
              val entryKey = entry.node.key.get
              entryKey.substring(path.pathStringLength, entryKey.length - valueNode.length - 1) :: Nil
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

  override protected def get(path: KeyValueStorePath): Future[Option[String]] = {
    httpClient.get[Option[EtcdKeyValue]](urlOfValue(path), logError = false).recover { case _ ⇒ None } map {
      _.flatMap(_.node.value)
    }
  }

  override protected def set(path: KeyValueStorePath, data: Option[String]): Future[Any] = data match {
    case None        ⇒ httpClient.delete(urlOfValue(path), logError = false).recover { case _ ⇒ false }
    case Some(value) ⇒ httpClient.httpWithEntity[Any](HttpMethods.PUT, urlOfValue(path), Option(FormData("value" → value).toEntity), logError = false)
  }

  private def urlOfValue(path: KeyValueStorePath) = s"${urlOf(path)}/$valueNode"

  private def urlOf(path: KeyValueStorePath, recursive: Boolean = false) = s"$url/v2/keys${path.toPathString}${if (recursive) "?recursive=true" else ""}"
}
