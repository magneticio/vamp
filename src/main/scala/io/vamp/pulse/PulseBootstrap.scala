package io.vamp.pulse

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.pulse.notification.PulseNotificationProvider

object PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = List(
    IoC.createActor[PulseActor]
  )
}
