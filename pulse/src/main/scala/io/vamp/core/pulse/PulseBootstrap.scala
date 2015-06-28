package io.vamp.core.pulse

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.pulse.elasticsearch.PulseInitializationActor
import io.vamp.core.pulse.notification.PulseNotificationProvider

object PulseBootstrap extends Bootstrap with PulseNotificationProvider {

  import Bootstrap._

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PulseInitializationActor) ! Start
    ActorSupport.actorOf(PulseActor) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    ActorSupport.actorFor(PulseInitializationActor) ! Shutdown
    ActorSupport.actorFor(PulseActor) ! Shutdown
  }
}
