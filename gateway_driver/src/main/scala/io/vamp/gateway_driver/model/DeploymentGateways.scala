package io.vamp.gateway_driver.model

import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService, Port }

case class DeploymentGateways(clusterGateways: List[ClusterGateway], endpointGateways: List[EndpointGateway])

trait DeploymentGateway {
  def port: Int

  def services: List[GatewayService]
}

case class ClusterGateway(matching: (Deployment, DeploymentCluster, Port) ⇒ Boolean, port: Int, services: List[GatewayService]) extends DeploymentGateway

case class GatewayService(matching: (DeploymentService) ⇒ Boolean, weight: Int, servers: List[Server], filters: List[Filter])

case class EndpointGateway(matching: (Deployment, Option[Port]) ⇒ Boolean, port: Int, services: List[GatewayService]) extends DeploymentGateway

