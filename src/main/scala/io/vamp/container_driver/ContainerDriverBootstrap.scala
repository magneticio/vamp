package io.vamp.container_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.common.spi.ServiceProvider
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedContainerDriverError }

object ContainerDriverBootstrap extends ActorBootstrap with ContainerDriverNotificationProvider {

  val `type` = Config.string("vamp.container-driver.type").toLowerCase

  def createActors(implicit actorSystem: ActorSystem) = {

    val actor = ServiceProvider.get(`type`, classOf[ContainerDriverServiceMapper]) match {
      case Some(clazz) ⇒
        IoC.alias(classOf[ContainerDriverActor], clazz)
        IoC.createActor(clazz)

      case _ ⇒ throwException(UnsupportedContainerDriverError(`type`))
    }

    actor :: Nil
  }
}
