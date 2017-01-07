package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.kv.KeyValueStoreActor

object PersistenceBootstrap {
  val databaseType = () ⇒ Config.string("vamp.persistence.database.type")().toLowerCase
  val keyValueStoreType = () ⇒ Config.string("vamp.persistence.key-value-store.type")().toLowerCase
}

class PersistenceBootstrap extends ActorBootstrap {

  import PersistenceBootstrap._

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val dbActor = alias[PersistenceActor](databaseType(), (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported database type: ${`type`}")
    })

    val kvActor = alias[KeyValueStoreActor](keyValueStoreType(), (`type`: String) ⇒ {
      throw new RuntimeException(s"Unsupported key-value store type: ${`type`}")
    })

    kvActor :: dbActor :: Nil
  }
}
