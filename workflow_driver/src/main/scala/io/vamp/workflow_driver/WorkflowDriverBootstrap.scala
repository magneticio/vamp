package io.vamp.workflow_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

object WorkflowDriverBootstrap extends Bootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {
    ConfigFactory.load().getString("vamp.container-driver.type").toLowerCase match {
      case ""        ⇒ Nil
      case "chronos" ⇒ IoC.createActor[WorkflowDriverActor](new ChronosWorkflowDriver(actorSystem.dispatcher)) :: Nil
      case value     ⇒ throwException(UnsupportedWorkflowDriverError(value))
    }
  }
}
