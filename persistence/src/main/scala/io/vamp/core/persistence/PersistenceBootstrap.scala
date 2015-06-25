package io.vamp.core.persistence

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.{ActorSupport, Bootstrap}

object PersistenceBootstrap extends Bootstrap {

  lazy val persistence = ConfigFactory.load().getString("vamp.core.persistence.storage-type") match {
    case "in-memory" => InMemoryPersistenceActor
    case "elasticsearch" => ElasticsearchPersistenceActor
    case _ => JdbcPersistenceActor
  }

  def run(implicit actorSystem: ActorSystem) = {

    ActorSupport.alias(PersistenceActor, ArchivePersistenceActor)

    if (persistence == ElasticsearchPersistenceActor)
      ActorSupport.actorOf(ElasticsearchPersistenceInitializationActor) ! Start

    ActorSupport.actorOf(persistence)
    ActorSupport.actorOf(ArchivePersistenceActor, persistence) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    if (persistence == ElasticsearchPersistenceActor)
      ActorSupport.actorFor(ElasticsearchPersistenceInitializationActor) ! Shutdown

    ActorSupport.actorFor(PersistenceActor) ! Shutdown
  }
}
