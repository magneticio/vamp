package io.vamp.core.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.container_driver.docker.DockerDriver
import io.vamp.core.container_driver.marathon.MarathonDriver
import io.vamp.core.container_driver.notification.{ContainerDriverNotificationProvider, UnsupportedContainerDriverError}

object ContainerDriverBootstrap extends Bootstrap with ContainerDriverNotificationProvider {

  def run(implicit actorSystem: ActorSystem) = {

    val driver = ConfigFactory.load().getString("vamp.core.container-driver.type").toLowerCase match {
      case "marathon" => new MarathonDriver(actorSystem.dispatcher, ConfigFactory.load().getString("vamp.core.container-driver.url"))
      case "docker"   => new DockerDriver(actorSystem.dispatcher)
      case value => throwException(UnsupportedContainerDriverError(value))
    }

    ActorSupport.actorOf(ContainerDriverActor, driver)
  }
}
