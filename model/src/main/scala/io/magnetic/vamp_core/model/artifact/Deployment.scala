package io.magnetic.vamp_core.model.artifact

import java.time.OffsetDateTime

import io.magnetic.vamp_common.notification.Notification

object DeploymentService {

  trait State {
    def startedAt: OffsetDateTime
  }

  case class ReadyForDeployment(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class Deployed(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class ReadyForUndeployment(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class Error(notification: Notification, startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

}

trait DeploymentState {
  def state: DeploymentService.State
}

case class Deployment(name: String, clusters: List[DeploymentCluster], endpoints: List[Port], parameters: Map[Trait.Name, Any]) extends Blueprint

case class DeploymentCluster(name: String, services: List[DeploymentService], sla: Option[Sla], routes: Map[Int, Int] = Map()) extends AbstractCluster

case class DeploymentService(state: DeploymentService.State, breed: DefaultBreed, scale: Option[DefaultScale], routing: Option[DefaultRouting], servers: List[DeploymentServer], dependencies: Map[String, String] = Map()) extends AbstractService with DeploymentState

case class DeploymentServer(name: String, host: String, ports: Map[Int, Int])
