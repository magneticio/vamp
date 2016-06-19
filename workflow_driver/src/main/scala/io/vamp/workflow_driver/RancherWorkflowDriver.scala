package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.IoC
import io.vamp.container_driver.DockerApp
import io.vamp.container_driver.rancher.{ LaunchConfig, RancherDriverActor }
import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.Future

class RancherWorkflowDriver(implicit actorSystem: ActorSystem) extends DaemonWorkflowDriver {

  protected val namePrefixConfig = "rancher.name-prefix"

  override def info: Future[Map[_, _]] = Future.successful(Map("rancher" -> Map("url" -> RancherDriverActor.rancherUrl)))

  override protected def driverActor: ActorRef = IoC.actorFor[RancherDriverActor]

  override protected def app(scheduledWorkflow: ScheduledWorkflow): DockerApp = {
    val app = super.app(scheduledWorkflow)
    app.copy(container = app.container.map(_.copy(network = LaunchConfig.defaultNetworkMode)))
  }
}
