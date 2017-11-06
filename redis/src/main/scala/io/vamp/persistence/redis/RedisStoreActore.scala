package io.vamp.persistence.redis

import io.vamp.common.{ ClassMapper, Config }
import io.vamp.persistence.KeyValueStoreActor
import redis.RedisClient

import scala.concurrent.Future
import scala.util.Try

class RedisStoreActorMapper extends ClassMapper {
  val name = "redis"
  val clazz: Class[_] = classOf[RedisStoreActor]
}

class RedisStoreActor extends KeyValueStoreActor {

  private lazy val host = Config.string("vamp.persistence.key-value-store.redis.host")()

  private lazy val port = Config.int("vamp.persistence.key-value-store.redis.port")()

  private lazy val client = RedisClient(host, port)

  override protected def info(): Future[Map[_, _]] = client.info("server").map { server ⇒
    val info = Try {
      server.lines.filterNot(_.startsWith("#")).map { line ⇒
        val index = line.indexOf(':')
        line.substring(0, index) → line.substring(index + 1)
      }.toMap
    }.getOrElse(Map())
    Map("type" → "redis", "redis" → Map("host" → host, "port" → port, "server" → info))
  }

  override protected def children(path: List[String]): Future[List[String]] = {
    val key = pathToString(path)
    client.keys(s"$key/*").map { list ⇒ list.flatMap(_.substring(key.length + 1).split('/').headOption).toList }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    client.get[String](pathToString(path))
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = {
    data match {
      case Some(value) ⇒ client.set(pathToString(path), value)
      case None        ⇒ client.del(pathToString(path))
    }
  }
}
