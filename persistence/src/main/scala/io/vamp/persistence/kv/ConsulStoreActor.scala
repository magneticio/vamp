package io.vamp.persistence.kv

import java.util.Base64

import io.vamp.common.config.Config
import io.vamp.common.http.RestClient

import scala.concurrent.Future

class ConsulStoreActor extends KeyValueStoreActor {

  private val url = Config.string("vamp.persistence.key-value-store.consul.url")

  override protected def info(): Future[Any] = RestClient.get[Any](s"$url/v1/agent/self") map {
    case consul ⇒ Map(
      "type" -> "consul",
      "consul" -> consul
    )
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    RestClient.get[Any](urlOf(path), RestClient.jsonHeaders, logError = false) recover { case _ ⇒ None } map {
      case head :: Nil    ⇒ Option(result(head.asInstanceOf[Map[_, _]]))
      case map: Map[_, _] ⇒ Option(result(map))
      case _              ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ RestClient.delete(urlOf(path), RestClient.jsonHeaders, logError = false)
    case Some(value) ⇒ RestClient.put[String](urlOf(path), value, List("Accept" -> "application/json", "Content-Type" -> "text/plain"))
  }

  private def urlOf(path: List[String], recurse: Boolean = false) = s"$url/v1/kv${KeyValueStoreActor.pathToString(path)}${if (recurse) "?recurse" else ""}"

  private def result(map: Map[_, _]): String = {
    map.asInstanceOf[Map[String, _]].get("Value").map(value ⇒ Base64.getDecoder.decode(value.asInstanceOf[String])).map(new String(_)).getOrElse("")
  }
}
