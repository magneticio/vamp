package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.{ ElasticsearchPersistenceInitializationActor, MySqlPersistenceInitializationActor }
import io.vamp.lifter.pulse.ElasticsearchPulseInitializationActor
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap

import scala.concurrent.{ ExecutionContext, Future }

class LifterBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {

    val pulseEnabled = Config.boolean("vamp.lifter.pulse.enabled")()
    val artifactEnabled = Config.boolean("vamp.lifter.artifact.enabled")()

    val persistence = if (Config.boolean("vamp.lifter.persistence.enabled")()) createPersistenceActors else Nil

    val pulse = if (pulseEnabled) createPulseActors else Nil

    val artifact = if (artifactEnabled) createArtifactActors else Nil

    implicit val ec: ExecutionContext = actorSystem.dispatcher
    Future.sequence(persistence ++ pulse ++ artifact)
  }

  protected def createPersistenceActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): List[Future[ActorRef]] = {
    PersistenceBootstrap.databaseType().toLowerCase match {
      case "mysql"         ⇒ IoC.createActor[MySqlPersistenceInitializationActor] :: Nil
      case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
      case _               ⇒ Nil
    }
  }

  protected def createPulseActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): List[Future[ActorRef]] = {
    PulseBootstrap.`type`().toLowerCase match {
      case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPulseInitializationActor] :: Nil
      case _               ⇒ Nil
    }
  }

  protected def createArtifactActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): List[Future[ActorRef]] = {
    IoC.createActor[ArtifactInitializationActor] :: Nil
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {}
}
