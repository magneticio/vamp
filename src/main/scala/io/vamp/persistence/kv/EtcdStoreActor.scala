package io.vamp.persistence.kv

import java.net.URLEncoder

import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model.{ HttpCharsets, MediaTypes }
import io.vamp.common.config.Config

import scala.concurrent.Future

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

  override protected def all(path: List[String]): Future[List[String]] = ???

  override protected def get(path: List[String]): Future[Option[String]] = {
    restClient.get[Any](urlOf(path), logError = false) recover { case _ ⇒ None } map {
      case map: Map[_, _] ⇒ map.asInstanceOf[Map[String, _]].get("node").map(_.asInstanceOf[Map[String, _]]).getOrElse(Map()).get("value").map(_.toString)
      case _              ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ restClient.delete(urlOf(path), logError = false)
    case Some(value) ⇒ restClient.put[Any](urlOf(path), s"value=${URLEncoder.encode(value, "UTF-8")}", contentType = WithCharset(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`))
  }

  private def urlOf(path: List[String], recursive: Boolean = false) = s"$url/v2/keys${KeyValueStoreActor.pathToString(path)}${if (recursive) "?recursive=true" else ""}"
}
