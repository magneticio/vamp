package io.vamp.persistence.operation

import io.vamp.model.artifact.{ Artifact, Gateway, RouteTarget }

case class GatewayPort(name: String, port: Int) extends Artifact {
  val kind = "gateway-port"
}

case class GatewayServiceAddress(name: String, host: String, port: Int) extends Artifact {
  val kind = "gateway-service-address"
}

case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends Artifact {
  val kind = "gateway-deployment-status"
}

case class RouteTargets(name: String, targets: List[RouteTarget]) extends Artifact {
  val kind = "route-targets"
}

case class InnerGateway(gateway: Gateway) extends Artifact {
  val name = gateway.name

  val kind = "inner-gateway"
}
