package io.vamp.container_driver

import akka.actor.ActorSystem
import io.vamp.common.{ Config, NamespaceResolver }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap {
  def `type`()(implicit namespaceResolver: NamespaceResolver) = Config.string("vamp.container-driver.type")().toLowerCase
}

class ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    logger.info(s"Container driver: ${ContainerDriverBootstrap.`type`()}")
    alias[ContainerDriverActor](ContainerDriverBootstrap.`type`(), (`type`: String) ⇒ {
      throwException(UnsupportedContainerDriverError(`type`))
    }) :: Nil
  }
}
