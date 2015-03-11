package io.magnetic.vamp_core.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.container_driver.marathon.MarathonDriver
import io.magnetic.vamp_core.container_driver.notification.{ContainerDriverNotificationProvider, UnsupportedContainerDriverError}

object ContainerDriverBootstrap extends Bootstrap with ContainerDriverNotificationProvider {

  def run(implicit actorSystem: ActorSystem) = {

    val driver = ConfigFactory.load().getString("deployment.container.driver.type").toLowerCase match {
      case "marathon" => new MarathonDriver(actorSystem.dispatcher, ConfigFactory.load().getString("deployment.container.driver.url"))
      case value => error(UnsupportedContainerDriverError(value))
    }

    ActorSupport.actorOf(ContainerDriverActor, driver)
  }
}
