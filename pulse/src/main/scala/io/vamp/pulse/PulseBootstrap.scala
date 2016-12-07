package io.vamp.pulse

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  val `type` = Config.string("vamp.pulse.type").toLowerCase

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    if (`type` != "elasticsearch") throwException(UnsupportedPulseDriverError(`type`))

    List(IoC.createActor[PulseActor])
  }
}
