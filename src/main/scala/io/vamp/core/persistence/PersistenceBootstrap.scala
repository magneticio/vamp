package io.vamp.core.persistence

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.{Bootstrap, IoC}

object PersistenceBootstrap extends Bootstrap {

  lazy val persistence = ConfigFactory.load().getString("vamp.core.persistence.storage-type") match {
    case "in-memory" => InMemoryPersistenceActor
    case "elasticsearch" => ElasticsearchPersistenceActor
    case _ => JdbcPersistenceActor
  }

  def run(implicit actorSystem: ActorSystem) = {

    IoC.alias(PersistenceActor, persistence)

    if (persistence == ElasticsearchPersistenceActor)
      IoC.createActor(ElasticsearchPersistenceInitializationActor) ! Start

    IoC.createActor(persistence) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    if (persistence == ElasticsearchPersistenceActor)
      IoC.actorFor(ElasticsearchPersistenceInitializationActor) ! Shutdown

    IoC.actorFor(PersistenceActor) ! Shutdown
  }
}
