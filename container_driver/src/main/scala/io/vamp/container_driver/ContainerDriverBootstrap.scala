package io.vamp.container_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

class ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    alias[ContainerDriverActor](Config.string("vamp.container-driver.type").toLowerCase, (`type`: String) ⇒ {
      throwException(UnsupportedContainerDriverError(`type`))
    }) :: Nil
  }
}
