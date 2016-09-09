package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.persistence.db.{ ElasticsearchPersistenceActor, InMemoryPersistenceActor, KeyValuePersistenceActor, PersistenceActor }
import io.vamp.persistence.kv.{ ConsulStoreActor, EtcdStoreActor, KeyValueStoreActor, ZooKeeperStoreActor }

object PersistenceBootstrap extends ActorBootstrap {

  val databaseType = Config.string("vamp.persistence.database.type")

  val keyValueStoreType = Config.string("vamp.persistence.key-value-store.type")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    IoC.alias[KeyValueStoreActor, ZooKeeperStoreActor]

    val dbActor = databaseType match {
      case "in-memory" ⇒
        IoC.alias[PersistenceActor, InMemoryPersistenceActor]
        IoC.createActor[InMemoryPersistenceActor]

      case "key-value" ⇒
        IoC.alias[PersistenceActor, KeyValuePersistenceActor]
        IoC.createActor[KeyValuePersistenceActor]

      case "elasticsearch" ⇒
        IoC.alias[PersistenceActor, ElasticsearchPersistenceActor]
        IoC.createActor[ElasticsearchPersistenceActor]

      case other ⇒ throw new RuntimeException(s"Unsupported database type: $other")
    }

    val kvActor = keyValueStoreType match {
      case "etcd" ⇒
        IoC.alias[KeyValueStoreActor, EtcdStoreActor]
        IoC.createActor[EtcdStoreActor]

      case "consul" ⇒
        IoC.alias[KeyValueStoreActor, ConsulStoreActor]
        IoC.createActor[ConsulStoreActor]

      case "zookeeper" ⇒
        IoC.alias[KeyValueStoreActor, ZooKeeperStoreActor]
        IoC.createActor[ZooKeeperStoreActor]

      case other ⇒ throw new RuntimeException(s"Unsupported key-value store type: $other")
    }

    kvActor :: dbActor :: Nil
  }
}
