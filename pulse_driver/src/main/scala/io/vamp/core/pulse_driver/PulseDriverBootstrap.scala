package io.vamp.core.pulse_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.pulse_driver.elasticsearch.ElasticsearchActor
import io.vamp.core.pulse_driver.notification.PulseDriverNotificationProvider

object PulseDriverBootstrap extends Bootstrap with PulseDriverNotificationProvider {

  import Bootstrap._

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(ElasticsearchActor) ! Start
    ActorSupport.actorOf(PulseDriverInitializationActor) ! Start
    ActorSupport.actorOf(PulseDriverActor)
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    ActorSupport.actorFor(PulseDriverInitializationActor) ! Shutdown
    ActorSupport.actorFor(ElasticsearchActor) ! Shutdown
  }
}
