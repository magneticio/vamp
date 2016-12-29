package io.vamp.pulse

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  val `type` = Config.string("vamp.pulse.type").toLowerCase

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val pulseActor = `type` match {
      case "no-store" ⇒
        IoC.alias[PulseActor, NoStorePulseActor]
        IoC.createActor[NoStorePulseActor]

      case "elasticsearch" ⇒
        IoC.alias[PulseActor, ElasticsearchPulseActor]
        IoC.createActor[ElasticsearchPulseActor]

      case _ ⇒ throwException(UnsupportedPulseDriverError(`type`))
    }

    pulseActor :: Nil
  }
}
