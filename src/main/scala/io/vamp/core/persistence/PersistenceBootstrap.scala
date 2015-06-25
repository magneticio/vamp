package io.vamp.core.persistence

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.{ActorSupport, Bootstrap}

object PersistenceBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    val persistence = ConfigFactory.load().getString("vamp.core.persistence.storage-type") match {
      case "in-memory" => InMemoryPersistenceActor
      case "elasticsearch" => CachePersistenceActor
      case _ => JdbcPersistenceActor
    }

    ActorSupport.alias(PersistenceActor, persistence)

    persistence match {

      case CachePersistenceActor =>
        ActorSupport.actorOf(ElasticsearchPersistenceInitializationActor) ! Start

        ActorSupport.actorOf(ElasticsearchPersistenceActor)
        ActorSupport.actorOf(CachePersistenceActor, ElasticsearchPersistenceActor) ! Start

      case _ =>
        ActorSupport.actorOf(PersistenceActor) ! Start
    }
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = ActorSupport.alias(PersistenceActor) match {

    case CachePersistenceActor =>
      ActorSupport.actorFor(CachePersistenceActor) ! Shutdown
      ActorSupport.actorFor(ElasticsearchPersistenceInitializationActor) ! Shutdown

    case _ =>
      ActorSupport.actorFor(PersistenceActor) ! Shutdown
  }

}

