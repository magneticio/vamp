package io.vamp.persistence.operation

import io.vamp.model.artifact.{ Artifact, Gateway, RouteTarget }

case class GatewayPort(name: String, port: Int) extends Artifact

case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends Artifact

case class RouteTargets(name: String, targets: List[RouteTarget]) extends Artifact

case class InnerGateway(gateway: Gateway) extends Artifact {
  val name = gateway.name
}
