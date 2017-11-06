package io.vamp.persistence.etcd

import akka.http.scaladsl.model._
import io.vamp.common.{ ClassMapper, Config }
import io.vamp.persistence.KeyValueStoreActor

import scala.concurrent.Future

case class EtcdKeyValue(node: EtcdNode)

case class EtcdNode(key: Option[String] = None, value: Option[String] = None, nodes: List[EtcdNode] = Nil)

class EtcdStoreActorMapper extends ClassMapper {
  val name = "etcd"
  val clazz: Class[_] = classOf[EtcdStoreActor]
}

class EtcdStoreActor extends KeyValueStoreActor {

  private lazy val url = Config.string("vamp.persistence.key-value-store.etcd.url")()

  private val valueNode = "value"

  override protected def info(): Future[Any] = for {
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

  override protected def children(path: List[String]): Future[List[String]] = {
    val key = pathToString(path)
    httpClient.get[EtcdKeyValue](urlOf(path), logError = false).recover { case _ ⇒ EtcdKeyValue(EtcdNode()) } map { entry ⇒
      entry.node.value match {
        case Some(_) ⇒
          val entryKey = entry.node.key.get
          entryKey.substring(key.length + 1).split('/').headOption.map(_ :: Nil).getOrElse(Nil)
        case _ ⇒
          entry.node.nodes.flatMap(_.key.get.substring(key.length + 1).split('/').headOption)
      }
    }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    httpClient.get[Option[EtcdKeyValue]](urlOfValue(path), logError = false).recover { case _ ⇒ None } map {
      _.flatMap(_.node.value)
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ httpClient.delete(urlOfValue(path), logError = false).recover { case _ ⇒ false }
    case Some(value) ⇒ httpClient.httpWithEntity[Any](HttpMethods.PUT, urlOfValue(path), Option(FormData("value" → value).toEntity), logError = false)
  }

  private def urlOfValue(path: List[String]) = s"${urlOf(path)}/$valueNode"

  private def urlOf(path: List[String], recursive: Boolean = false) = s"$url/v2/keys${pathToString(path)}${if (recursive) "?recursive=true" else ""}"
}
