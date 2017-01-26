package io.vamp.persistence.redis

import io.vamp.common.config.Config
import io.vamp.common.spi.ClassMapper
import io.vamp.persistence.KeyValueStoreActor
import redis.RedisClient

import scala.concurrent.Future
import scala.util.Try

class RedisStoreActorMapper extends ClassMapper {
  val name = "redis"
  val clazz = classOf[RedisStoreActor]
}

class RedisStoreActor extends KeyValueStoreActor {

  private val host = Config.string("vamp.persistence.key-value-store.redis.host")()

  private val port = Config.int("vamp.persistence.key-value-store.redis.port")()

  private val client = RedisClient(host, port)

  override protected def info(): Future[Map[_, _]] = client.info("server").map { server ⇒
    val info = Try {
      server.lines.filterNot(_.startsWith("#")).map { line ⇒
        val index = line.indexOf(':')
        line.substring(0, index) → line.substring(index + 1)
      }.toMap
    }.getOrElse(Map())
    Map("type" → "redis", "redis" → Map("host" → host, "port" → port, "server" → info))
  }

  override protected def all(path: List[String]): Future[List[String]] = {
    val key = KeyValueStoreActor.pathToString(path)
    client.keys(s"$key/*").map { list ⇒ list.map(_.substring(key.length + 1)).toList }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    client.get[String](KeyValueStoreActor.pathToString(path))
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = {
    data match {
      case Some(value) ⇒ client.set(KeyValueStoreActor.pathToString(path), value)
      case None        ⇒ client.del(KeyValueStoreActor.pathToString(path))
    }
  }
}
