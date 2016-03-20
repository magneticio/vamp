package io.vamp.pulse

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.pulse.notification.PulseNotificationProvider

object PulseBootstrap extends Bootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = List(
    IoC.createActor[PulseActor]
  )
}
