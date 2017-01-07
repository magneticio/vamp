package io.vamp.pulse

import akka.actor.ActorSystem
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap {
  val `type` = () ⇒ Config.string("vamp.pulse.type")().toLowerCase
}

class PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    alias[PulseActor](PulseBootstrap.`type`(), (`type`: String) ⇒ {
      throwException(UnsupportedPulseDriverError(`type`))
    }) :: Nil
  }
}
