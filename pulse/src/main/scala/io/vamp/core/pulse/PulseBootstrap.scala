package io.vamp.core.pulse

import akka.actor.ActorSystem
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.core.pulse.elasticsearch.PulseInitializationActor
import io.vamp.core.pulse.notification.PulseNotificationProvider

object PulseBootstrap extends Bootstrap with PulseNotificationProvider {

  import Bootstrap._

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor[PulseInitializationActor] ! Start
    IoC.createActor[PulseActor] ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[PulseInitializationActor] ! Shutdown
    IoC.actorFor[PulseActor] ! Shutdown
  }
}
