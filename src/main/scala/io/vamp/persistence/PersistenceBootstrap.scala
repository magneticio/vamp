package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ IoC, Bootstrap }
import io.vamp.persistence.db.{ PersistenceActor, InMemoryPersistenceActor, ElasticsearchPersistenceInitializationActor, ElasticsearchPersistenceActor }
import io.vamp.persistence.kv.{ KeyValueStoreActor, ZooKeeperGatewayStoreActor }

object PersistenceBootstrap extends Bootstrap {

  lazy val storageType = ConfigFactory.load().getString("vamp.persistence.storage-type")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    IoC.alias[KeyValueStoreActor, ZooKeeperGatewayStoreActor]

    val persistenceActors = storageType match {
      case "in-memory" ⇒
        IoC.alias[PersistenceActor, InMemoryPersistenceActor]
        IoC.createActor[InMemoryPersistenceActor] :: Nil

      case _ ⇒
        IoC.alias[PersistenceActor, ElasticsearchPersistenceActor]
        List(
          IoC.createActor[ElasticsearchPersistenceInitializationActor],
          IoC.createActor[ElasticsearchPersistenceActor]
        )
    }

    IoC.createActor[ZooKeeperGatewayStoreActor] :: persistenceActors
  }
}
