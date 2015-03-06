package io.magnetic.vamp_core.model.artifact

import java.time.OffsetDateTime

import io.magnetic.vamp_common.notification.Notification

object DeploymentService {

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
  def state: DeploymentService.State
}

case class Deployment(name: String, clusters: List[DeploymentCluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Blueprint

case class DeploymentCluster(name: String, services: List[DeploymentService], sla: Option[Sla]) extends AbstractCluster

case class DeploymentService(state: DeploymentService.State, breed: DefaultBreed, scale: Option[DefaultScale], servers: List[DeploymentServer], routing: Option[DefaultRouting]) extends AbstractService with DeploymentState

case class DeploymentServer(host: String)
