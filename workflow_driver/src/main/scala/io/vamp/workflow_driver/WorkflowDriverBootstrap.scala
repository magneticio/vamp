package io.vamp.workflow_driver

import akka.actor.ActorSystem
import io.vamp.common.config.Config
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

import scala.language.postfixOps

object WorkflowDriverBootstrap extends ActorBootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem) = {

    val config = Config.config("vamp.workflow-driver")

    val drivers: List[WorkflowDriver] = config.string("type").toLowerCase.split(',').map(_.trim).collect {
      case "docker"                 ⇒ new DockerWorkflowDriver
      case "chronos"                ⇒ new ChronosWorkflowDriver(config.string("chronos.url"))
      case "rancher"                ⇒ new RancherWorkflowDriver
      case "marathon"               ⇒ new MarathonWorkflowDriver
      case "kubernetes"             ⇒ new KubernetesWorkflowDriver
      case value if value != "none" ⇒ throwException(UnsupportedWorkflowDriverError(value))
    } toList

    IoC.createActor[WorkflowDriverActor](drivers :+ new NoneWorkflowDriver) :: Nil
  }
}
