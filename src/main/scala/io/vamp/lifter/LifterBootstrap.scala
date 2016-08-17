package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.kibana.KibanaDashboardInitializationActor
import io.vamp.lifter.persistence.ElasticsearchPersistenceInitializationActor
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.persistence.PersistenceBootstrap

object LifterBootstrap extends ActorBootstrap {

  val config = Config.config("vamp.lifter")

  val pulseEnabled = config.boolean("pulse.enabled")

  val kibanaEnabled = config.boolean("kibana.enabled")

  val artifactEnabled = config.boolean("artifact.enabled")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val persistence = if (config.boolean("persistence.enabled")) {
      PersistenceBootstrap.databaseType match {
        case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
        case _               ⇒ Nil
      }
    } else Nil

    val pulse = if (pulseEnabled)
      IoC.createActor[PulseInitializationActor] :: Nil
    else Nil

    val kibana = if (kibanaEnabled)
      IoC.createActor[KibanaDashboardInitializationActor] :: Nil
    else Nil

    val artifact = if (artifactEnabled)
      IoC.createActor[ArtifactInitializationActor] :: Nil
    else Nil

    persistence ++ pulse ++ kibana ++ artifact
  }
}
