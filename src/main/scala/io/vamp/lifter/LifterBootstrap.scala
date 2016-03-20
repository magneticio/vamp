package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.lifter.kibana.KibanaDashboardInitializationActor
import io.vamp.lifter.persistence.ElasticsearchPersistenceInitializationActor
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.persistence.PersistenceBootstrap

object LifterBootstrap extends Bootstrap {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val persistence = PersistenceBootstrap.databaseType match {
      case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPersistenceInitializationActor] :: Nil
      case _               ⇒ Nil
    }

    val pulse = IoC.createActor[PulseInitializationActor]

    val kibana = IoC.createActor[KibanaDashboardInitializationActor]

    persistence :+ pulse :+ kibana
  }
}
