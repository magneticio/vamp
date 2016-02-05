package io.vamp.persistence.operation

import io.vamp.model.artifact.{ Artifact, RouteTarget }

case class GatewayPort(name: String, port: String) extends Artifact

case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends Artifact

case class RouteTargets(name: String, targets: List[RouteTarget]) extends Artifact