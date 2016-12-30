package io.vamp.container_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.config.Config
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap {
  val `type` = Config.string("vamp.container-driver.type").toLowerCase
}

class ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    alias[ContainerDriverActor](ContainerDriverBootstrap.`type`, (`type`: String) ⇒ {
      throwException(UnsupportedContainerDriverError(`type`))
    }) :: Nil
  }
}
