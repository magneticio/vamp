package io.vamp.workflow_driver

import akka.actor.ActorSystem
import io.vamp.common.config.Config
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

import scala.language.postfixOps

object WorkflowDriverBootstrap extends Bootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val config = Config.config("vamp.workflow-driver")

    val drivers: List[WorkflowDriver] = config.string("type").toLowerCase.split(',').map(_.trim).flatMap {
      case "none"       ⇒ Nil
      case "docker"     ⇒ new DockerWorkflowDriver :: Nil
      case "chronos"    ⇒ new ChronosWorkflowDriver(config.string("chronos.url")) :: Nil
      case "rancher"    ⇒ new RancherWorkflowDriver :: Nil
      case "marathon"   ⇒ new MarathonWorkflowDriver :: Nil
      case "kubernetes" ⇒ new KubernetesWorkflowDriver :: Nil
      case value        ⇒ throwException(UnsupportedWorkflowDriverError(value))
    } toList

    IoC.createActor[WorkflowDriverActor](drivers :+ NoneWorkflowDriver) :: Nil
  }
}
