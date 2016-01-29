package io.vamp.persistence.kv

import java.util.Base64

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient

import scala.concurrent.Future
import scala.language.postfixOps

class ConsulStoreActor extends KeyValueStoreActor {

  private val url = ConfigFactory.load().getString("vamp.persistence.key-value-store.consul.url")

  override protected def info(): Future[Any] = RestClient.get[Any](s"$url/v1/agent/self") map {
    case consul ⇒ Map(
      "type" -> "consul",
      "consul" -> consul
    )
  }

  override protected def all(path: List[String]): Future[Map[String, String]] = {
    RestClient.get[Any](urlOf(path, recurse = true), RestClient.jsonHeaders, logError = false) recover { case _ ⇒ None } map {
      case list: List[_] ⇒
        list.flatMap {
          case map: Map[_, _] ⇒ result(path, map) :: Nil
          case _              ⇒ Nil
        } toMap
      case _ ⇒ Map()
    }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    RestClient.get[Any](urlOf(path), RestClient.jsonHeaders, logError = false) recover { case _ ⇒ None } map {
      case head :: Nil    ⇒ Option(result(path, head.asInstanceOf[Map[_, _]])._2)
      case map: Map[_, _] ⇒ Option(result(path, map)._2)
      case _              ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Unit = data match {
    case None        ⇒ RestClient.delete(urlOf(path), RestClient.jsonHeaders, logError = false)
    case Some(value) ⇒ RestClient.put[String](urlOf(path), value, List("Accept" -> "application/json", "Content-Type" -> "text/plain"))
  }

  private def urlOf(path: List[String], recurse: Boolean = false) = s"$url/v1/kv/${pathToString(path)}${if (recurse) "?recurse" else ""}"

  private def result(path: List[String], map: Map[_, _]): (String, String) = {
    val key = map.asInstanceOf[Map[String, _]].get("Key").map(_.toString).getOrElse("").split('/').drop(path.size + 1).mkString("/")
    val value = map.asInstanceOf[Map[String, _]].get("Value").map(value ⇒ Base64.getDecoder.decode(value.asInstanceOf[String])).map(new String(_)).getOrElse("")
    key -> value
  }
}
