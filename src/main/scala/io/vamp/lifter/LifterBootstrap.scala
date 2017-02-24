package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.ElasticsearchPersistenceInitializationActor
import io.vamp.lifter.pulse.ElasticsearchPulseInitializationActor
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap

import scala.concurrent.{ ExecutionContext, Future }

class LifterBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {

    val pulseEnabled = Config.boolean("vamp.lifter.pulse.enabled")()
    val artifactEnabled = Config.boolean("vamp.lifter.artifact.enabled")()

    val persistence = if (Config.boolean("vamp.lifter.persistence.enabled")()) {
      PersistenceBootstrap.databaseType().toLowerCase match {
        case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
        case _               ⇒ Nil
      }
    } else Nil

    val pulse = if (pulseEnabled) {
      PulseBootstrap.`type`().toLowerCase match {
        case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPulseInitializationActor] :: Nil
        case _               ⇒ Nil
      }
    } else Nil

    val artifact = if (artifactEnabled)
      IoC.createActor[ArtifactInitializationActor] :: Nil
    else Nil

    implicit val ec: ExecutionContext = actorSystem.dispatcher
    Future.sequence(persistence ++ pulse ++ artifact)
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {}
}
