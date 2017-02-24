package io.vamp.container_driver

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap {
  def `type`()(implicit namespace: Namespace) = Config.string("vamp.container-driver.type")().toLowerCase
}

class ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    logger.info(s"Container driver: ${ContainerDriverBootstrap.`type`()}")
    alias[ContainerDriverActor](ContainerDriverBootstrap.`type`(), (`type`: String) ⇒ {
      throwException(UnsupportedContainerDriverError(`type`))
    }).map(_ :: Nil)(actorSystem.dispatcher)
  }
}
