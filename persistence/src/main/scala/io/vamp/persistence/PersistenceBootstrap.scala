package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.persistence.db.{ ElasticsearchPersistenceActor, ElasticsearchPersistenceInitializationActor, InMemoryPersistenceActor, PersistenceActor }
import io.vamp.persistence.kv.{ EtcdStoreActor, KeyValueStoreActor, ZooKeeperStoreActor }

object PersistenceBootstrap extends Bootstrap {

  val databaseType = ConfigFactory.load().getString("vamp.persistence.database.type")

  val keyValueStoreType = ConfigFactory.load().getString("vamp.persistence.key-value-store.type")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    IoC.alias[KeyValueStoreActor, ZooKeeperStoreActor]

    val dbActors = databaseType match {
      case "in-memory" ⇒
        IoC.alias[PersistenceActor, InMemoryPersistenceActor]
        IoC.createActor[InMemoryPersistenceActor] :: Nil

      case "elasticsearch" ⇒
        IoC.alias[PersistenceActor, ElasticsearchPersistenceActor]
        List(
          IoC.createActor[ElasticsearchPersistenceInitializationActor],
          IoC.createActor[ElasticsearchPersistenceActor]
        )

      case other ⇒ throw new RuntimeException(s"Unsupported database type: $other")
    }

    val kvActor = keyValueStoreType match {
      case "zookeeper" ⇒
        IoC.alias[KeyValueStoreActor, ZooKeeperStoreActor]
        IoC.createActor[ZooKeeperStoreActor]

      case "etcd" ⇒
        IoC.alias[KeyValueStoreActor, EtcdStoreActor]
        IoC.createActor[EtcdStoreActor]

      case other ⇒ throw new RuntimeException(s"Unsupported key-value store type: $other")
    }

    kvActor :: dbActors
  }
}
