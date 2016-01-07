package io.vamp.persistence

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.persistence.db.{ PersistenceActor, InMemoryPersistenceActor, ElasticsearchPersistenceInitializationActor, ElasticsearchPersistenceActor }

object PersistenceBootstrap extends Bootstrap {

  lazy val storageType = ConfigFactory.load().getString("vamp.persistence.storage-type")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = storageType match {
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
}
