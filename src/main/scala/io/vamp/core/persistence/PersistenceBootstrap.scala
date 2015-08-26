package io.vamp.core.persistence

import akka.actor.{ ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC }

object PersistenceBootstrap extends Bootstrap {

  lazy val storageType = ConfigFactory.load().getString("vamp.core.persistence.storage-type")

  def run(implicit actorSystem: ActorSystem) = storageType match {
    case "in-memory" ⇒
      IoC.alias[PersistenceActor, InMemoryPersistenceActor]
      IoC.createActor(Props(classOf[InMemoryPersistenceActor])) ! Start

    case "elasticsearch" ⇒
      IoC.alias[PersistenceActor, ElasticsearchPersistenceActor]
      IoC.createActor(Props(classOf[ElasticsearchPersistenceInitializationActor])) ! Start
      IoC.createActor(Props(classOf[ElasticsearchPersistenceActor])) ! Start

    case _ ⇒
      IoC.alias[PersistenceActor, JdbcPersistenceActor]
      IoC.createActor(Props(classOf[JdbcPersistenceActor])) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    if (storageType == "elasticsearch")
      IoC.actorFor[ElasticsearchPersistenceInitializationActor] ! Shutdown

    IoC.actorFor[PersistenceActor] ! Shutdown
  }
}
