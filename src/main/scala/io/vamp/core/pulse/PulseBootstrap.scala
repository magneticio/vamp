package io.vamp.core.pulse

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.pulse.notification.PulseNotificationProvider

object PulseBootstrap extends Bootstrap with PulseNotificationProvider {

  import Bootstrap._

  def run(implicit actorSystem: ActorSystem) = {
    //    ActorSupport.actorOf(ElasticsearchActor) ! Start
    //    ActorSupport.actorOf(PulseDriverInitializationActor) ! Start
    //    ActorSupport.actorOf(PulseDriverActor)


    ActorSupport.actorOf(PulseActor) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    //    ActorSupport.actorFor(PulseDriverInitializationActor) ! Shutdown
    //    ActorSupport.actorFor(ElasticsearchActor) ! Shutdown
    ActorSupport.actorFor(PulseActor) ! Shutdown
  }
}
