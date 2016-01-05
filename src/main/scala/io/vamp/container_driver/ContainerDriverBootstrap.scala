package io.vamp.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.container_driver.docker.DockerDriver
import io.vamp.container_driver.marathon.MarathonDriver
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap extends Bootstrap with ContainerDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val driver = ConfigFactory.load().getString("vamp.container-driver.type").toLowerCase match {
      case "marathon" ⇒ new MarathonDriver(actorSystem.dispatcher, ConfigFactory.load().getString("vamp.container-driver.url"))
      case "docker"   ⇒ new DockerDriver(actorSystem.dispatcher)
      case value      ⇒ throwException(UnsupportedContainerDriverError(value))
    }

    IoC.createActor[ContainerDriverActor](driver) :: Nil
  }
}
