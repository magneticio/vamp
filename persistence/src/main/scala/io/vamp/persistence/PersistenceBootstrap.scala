package io.vamp.persistence

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC }

object PersistenceBootstrap extends Bootstrap {

  lazy val storageType = ConfigFactory.load().getString("vamp.persistence.storage-type")

  def run(implicit actorSystem: ActorSystem) = storageType match {
    case "in-memory" ⇒
      IoC.alias[PersistenceActor, InMemoryPersistenceActor]
      IoC.createActor[InMemoryPersistenceActor] ! Start

    case "elasticsearch" ⇒
      IoC.alias[PersistenceActor, ElasticsearchPersistenceActor]
      IoC.createActor[ElasticsearchPersistenceInitializationActor] ! Start
      IoC.createActor[ElasticsearchPersistenceActor] ! Start

    case _ ⇒
      IoC.alias[PersistenceActor, JdbcPersistenceActor]
      IoC.createActor[JdbcPersistenceActor] ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    if (storageType == "elasticsearch")
      IoC.actorFor[ElasticsearchPersistenceInitializationActor] ! Shutdown

    IoC.actorFor[PersistenceActor] ! Shutdown
  }
}
