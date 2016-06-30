package io.vamp.container_driver

import akka.actor.ActorSystem
import io.vamp.common.config.Config
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.container_driver.docker.DockerDriverActor
import io.vamp.container_driver.kubernetes.KubernetesDriverActor
import io.vamp.container_driver.marathon.MarathonDriverActor
import io.vamp.container_driver.rancher.RancherDriverActor
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  private val configuration = Config.config("vamp.container-driver")

  val `type` = configuration.string("type").toLowerCase

  def createActors(implicit actorSystem: ActorSystem) = {

    val actor = `type` match {

      case "docker" ⇒
        IoC.alias[ContainerDriverActor, DockerDriverActor]
        IoC.createActor[DockerDriverActor]

      case "kubernetes" ⇒
        IoC.alias[ContainerDriverActor, KubernetesDriverActor]
        IoC.createActor[KubernetesDriverActor]

      case "marathon" ⇒
        IoC.alias[ContainerDriverActor, MarathonDriverActor]
        IoC.createActor[MarathonDriverActor]

      case "rancher" ⇒
        IoC.alias[ContainerDriverActor, RancherDriverActor]
        IoC.createActor[RancherDriverActor]

      case value ⇒ throwException(UnsupportedContainerDriverError(value))
    }

    actor :: Nil
  }
}
