package io.vamp.workflow_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

import scala.language.postfixOps

object WorkflowDriverBootstrap extends Bootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val config = ConfigFactory.load().getConfig("vamp.workflow-driver")

    val drivers: List[WorkflowDriver] = config.getString("type").toLowerCase.split(',').map(_.trim).flatMap {
      case "none"     ⇒ Nil
      case "chronos"  ⇒ new ChronosWorkflowDriver(actorSystem.dispatcher, config.getString("chronos.url")) :: Nil
      case "marathon" ⇒ new MarathonWorkflowDriver :: Nil
      case value      ⇒ throwException(UnsupportedWorkflowDriverError(value))
    } toList

    IoC.createActor[WorkflowDriverActor](drivers :+ NoneWorkflowDriver) :: Nil
  }
}
