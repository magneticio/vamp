package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.{ ExecutionContext, Future }

object PersistenceBootstrap {

  def databaseType()(implicit namespace: Namespace): String = Config.string("vamp.persistence.database.type")().toLowerCase

  def keyValueStoreType()(implicit namespace: Namespace): String = Config.string("vamp.persistence.key-value-store.type")().toLowerCase
}

class PersistenceBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    for {
      //storage ← new PersistenceStorageBootstrap().createActors
      keyValue ← new KeyValueBootstrap().createActors
    } yield keyValue
  }
}

class PersistenceStorageBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val db = databaseType()
    info(s"Database: $db")

    val dbActor = alias[PersistenceActor](db, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported database type: ${`type`}")
    })

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    dbActor.map(_ :: Nil)
  }
}

class KeyValueBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val kv = keyValueStoreType()
    info(s"Key-Value store: $kv")

    val kvActor = alias[KeyValueStoreActor](kv, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported key-value store type: ${`type`}")
    })

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    kvActor.map(_ :: Nil)
  }
}
