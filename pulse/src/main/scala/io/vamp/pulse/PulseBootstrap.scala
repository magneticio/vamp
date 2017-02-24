package io.vamp.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap {
  def `type`()(implicit namespace: Namespace) = Config.string("vamp.pulse.type")().toLowerCase
}

class PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    logger.info(s"Pulse: ${PulseBootstrap.`type`()}")
    alias[PulseActor](PulseBootstrap.`type`(), (`type`: String) ⇒ {
      throwException(UnsupportedPulseDriverError(`type`))
    }).map(_ :: Nil)(actorSystem.dispatcher)
  }
}
