package io.vamp.workflow_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

object WorkflowDriverBootstrap extends Bootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load().getConfig("vamp.workflow-driver")

    config.getString("type").toLowerCase match {
      case ""        ⇒ Nil
      case "chronos" ⇒ IoC.createActor[WorkflowDriverActor](new ChronosWorkflowDriver(actorSystem.dispatcher, config.getString("chronos.url"))) :: Nil
      case value     ⇒ throwException(UnsupportedWorkflowDriverError(value))
    }
  }
}
