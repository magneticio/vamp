package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.ActorBootstrap

import scala.concurrent.{ ExecutionContext, Future }

object PersistenceBootstrap {

  def databaseType()(implicit namespace: Namespace) = {
    Config.string("vamp.persistence.database.type")().toLowerCase
  }

  def keyValueStoreType()(implicit namespace: Namespace) = {
    Config.string("vamp.persistence.key-value-store.type")().toLowerCase
  }
}

class PersistenceBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {

    val db = databaseType()
    val kv = keyValueStoreType()

    val dbActor = alias[PersistenceActor](db, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported database type: ${`type`}")
    })

    val kvActor = alias[KeyValueStoreActor](kv, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported key-value store type: ${`type`}")
    })

    info(s"Database: $db")
    info(s"Key-Value store: $kv")

    implicit val ec: ExecutionContext = actorSystem.dispatcher
    Future.sequence(kvActor :: dbActor :: Nil)
  }
}
