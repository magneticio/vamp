package io.magnetic.vamp_core.model.artifact

import java.time.OffsetDateTime

import io.magnetic.vamp_common.notification.Notification

object Deployment {

  trait State {
    def initiated: OffsetDateTime
  }

  trait Regular extends State {
    def completed: Option[OffsetDateTime]
  }

  case class ReadyForDeployment(initiated: OffsetDateTime = OffsetDateTime.now(), completed: Option[OffsetDateTime] = None) extends State with Regular

  case class Deployed(initiated: OffsetDateTime = OffsetDateTime.now(), completed: Option[OffsetDateTime] = None) extends State with Regular

  case class ReadyForUndeployment(initiated: OffsetDateTime = OffsetDateTime.now(), completed: Option[OffsetDateTime] = None) extends State with Regular

  case class Undeployed(initiated: OffsetDateTime = OffsetDateTime.now(), completed: Option[OffsetDateTime] = None) extends State with Regular

  case class ReadyForRemoval(initiated: OffsetDateTime = OffsetDateTime.now(), completed: Option[OffsetDateTime] = None) extends State with Regular

  case class Error(notification: Notification, initiated: OffsetDateTime = OffsetDateTime.now()) extends State

}

trait DeploymentState {
  def state: Deployment.State
}

case class Deployment(name: String, state: Deployment.State, clusters: List[DeploymentCluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Blueprint with DeploymentState

case class DeploymentCluster(name: String, state: Deployment.State, services: List[DeploymentService], sla: Option[Sla]) extends AbstractCluster with DeploymentState

case class DeploymentService(state: Deployment.State, breed: DefaultBreed, scale: Option[Scale], routing: Option[Routing]) extends AbstractService with DeploymentState
