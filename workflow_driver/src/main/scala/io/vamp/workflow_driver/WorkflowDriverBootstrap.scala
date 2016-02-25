package io.vamp.workflow_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

object WorkflowDriverBootstrap extends Bootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load().getConfig("vamp.workflow-driver")

    val driver = config.getString("type").toLowerCase match {
      case "none"    ⇒ NoneWorkflowDriver
      case "chronos" ⇒ new ChronosWorkflowDriver(actorSystem.dispatcher, config.getString("chronos.url"))
      case value     ⇒ throwException(UnsupportedWorkflowDriverError(value))
    }

    IoC.createActor[WorkflowDriverActor](driver) :: Nil
  }
}
