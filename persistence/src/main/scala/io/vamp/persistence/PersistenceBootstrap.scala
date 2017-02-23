package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.{ Config, NamespaceResolver }
import io.vamp.common.akka.ActorBootstrap

object PersistenceBootstrap {
  def databaseType()(implicit namespaceResolver: NamespaceResolver) = {
    Config.string("vamp.persistence.database.type")().toLowerCase
  }

  def keyValueStoreType()(implicit namespaceResolver: NamespaceResolver) = {
    Config.string("vamp.persistence.key-value-store.type")().toLowerCase
  }
}

class PersistenceBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val db = databaseType()
    val kv = keyValueStoreType()

    val dbActor = alias[PersistenceActor](db, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported database type: ${`type`}")
    })

    val kvActor = alias[KeyValueStoreActor](kv, (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported key-value store type: ${`type`}")
    })

    logger.info(s"Database: $db")
    logger.info(s"KV store: $kv")

    kvActor :: dbActor :: Nil
  }
}
