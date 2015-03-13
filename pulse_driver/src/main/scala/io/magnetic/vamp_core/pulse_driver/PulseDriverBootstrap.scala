package io.magnetic.vamp_core.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.container_driver.notification.PulseDriverNotificationProvider
import io.magnetic.vamp_core.pulse_driver.{PulseDriverActor, DefaultPulseDriver}

object PulseDriverBootstrap extends Bootstrap with PulseDriverNotificationProvider {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PulseDriverActor, new DefaultPulseDriver(actorSystem.dispatcher, ConfigFactory.load().getString("deployment.pulse.driver.url")))
  }
}
