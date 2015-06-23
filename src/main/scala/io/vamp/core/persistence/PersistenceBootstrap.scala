package io.vamp.core.persistence

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.Start
import io.vamp.common.akka.{ActorSupport, Bootstrap}

object PersistenceBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    val persistence = ConfigFactory.load().getString("vamp.core.persistence.storage-type") match {
      case "in-memory" => InMemoryPersistenceActor
      case "elasticsearch" => ElasticsearchPersistenceActor
      case _ => JdbcPersistenceActor
    }

    ActorSupport.alias(PersistenceActor, persistence)

    persistence match {
      case ElasticsearchPersistenceActor => ActorSupport.actorOf(ElasticsearchPersistenceInitializationActor) ! Start
      case _ =>
    }

    ActorSupport.actorOf(PersistenceActor)
  }
}

