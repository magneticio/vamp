package io.vamp.pulse

import akka.actor.ActorSystem
import io.vamp.common.{ Config, NamespaceResolver }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap {
  def `type`()(implicit namespaceResolver: NamespaceResolver) = Config.string("vamp.pulse.type")().toLowerCase
}

class PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    logger.info(s"Pulse: ${PulseBootstrap.`type`()}")
    alias[PulseActor](PulseBootstrap.`type`(), (`type`: String) ⇒ {
      throwException(UnsupportedPulseDriverError(`type`))
    }) :: Nil
  }
}
