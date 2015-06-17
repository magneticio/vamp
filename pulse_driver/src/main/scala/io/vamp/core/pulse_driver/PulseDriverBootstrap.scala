package io.vamp.core.pulse_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.pulse_driver.notification.PulseDriverNotificationProvider

object PulseDriverBootstrap extends Bootstrap with PulseDriverNotificationProvider {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PulseDriverActor)
  }
}
