package io.vamp.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.container_driver.docker.DockerDriver
import io.vamp.container_driver.marathon.MarathonDriver
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap extends Bootstrap with ContainerDriverNotificationProvider {

  val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  def createActors(implicit actorSystem: ActorSystem) = {

    val `type` = ConfigFactory.load().getString("vamp.container-driver.type").toLowerCase

    val driver = `type` match {
      case "docker"   ⇒ new DockerDriver(actorSystem.dispatcher)
      case "marathon" ⇒ new MarathonDriver(actorSystem.dispatcher, configuration.getString("mesos.url"), configuration.getString("marathon.url"))
      case value      ⇒ throwException(UnsupportedContainerDriverError(value))
    }

    IoC.createActor[ContainerDriverActor](`type`, driver) :: Nil
  }
}
