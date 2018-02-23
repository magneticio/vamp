package io.vamp.persistence

import akka.actor.Actor
import io.vamp.model.artifact.{ DefaultRoute, _ }

trait GatewayPersistenceMessages {

  case class CreateInternalGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayServiceAddress(gateway: Gateway, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayPort(gateway: Gateway, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayServiceAddress(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayDeploymentStatus(gateway: Gateway, deployed: Boolean) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayRouteTargets(route: DefaultRoute, targets: List[RouteTarget]) extends PersistenceActor.PersistenceMessages

  case class UpdateInternalGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class DeleteInternalGateway(name: String) extends PersistenceActor.PersistenceMessages

  case class DeleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port) extends PersistenceActor.PersistenceMessages

  case class ResetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

}

trait GatewayPersistenceOperations {
  this: PatchPersistenceOperations ⇒

  import DeploymentPersistenceOperations._
  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: CreateInternalGateway         ⇒ replyUpdate(InternalGateway(o.gateway))

    case o: CreateGatewayServiceAddress   ⇒ replyUpdate(GatewayServiceAddress(o.gateway.name, o.host, o.port))

    case o: CreateGatewayPort             ⇒ replyUpdate(GatewayPort(o.gateway.name, o.port))

    case o: UpdateGatewayServiceAddress   ⇒ replyUpdate(GatewayServiceAddress(serviceArtifactName(o.deployment, o.cluster, o.service), o.host, o.port))

    case o: UpdateGatewayDeploymentStatus ⇒ replyUpdate(GatewayDeploymentStatus(o.gateway.name, o.deployed))

    case o: UpdateGatewayRouteTargets     ⇒ replyUpdate(RouteTargets(o.route.path.normalized, o.targets))

    case o: UpdateInternalGateway         ⇒ replyUpdate(InternalGateway(o.gateway))

    case o: DeleteInternalGateway         ⇒ replyDelete(o.name, classOf[InternalGateway])

    case o: DeleteGatewayRouteTargets     ⇒ replyDelete(servicePortArtifactName(o.deployment, o.cluster, o.service, o.port), classOf[RouteTargets])

    case o: ResetGateway                  ⇒ resetGateway(o.deployment, o.cluster, o.service)
  }

  private def resetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    val name = serviceArtifactName(deployment, cluster, service)
    List(
      classOf[GatewayPort],
      classOf[GatewayServiceAddress],
      classOf[GatewayDeploymentStatus],
      classOf[RouteTargets],
      classOf[InternalGateway]
    ).foreach(t ⇒ replyDelete(name, t))
  }
}

private[persistence] object GatewayPort {
  val kind: String = "gateway-ports"
}

private[persistence] case class GatewayPort(name: String, port: Int) extends PersistenceArtifact {
  val kind: String = GatewayPort.kind
}

private[persistence] object GatewayServiceAddress {
  val kind: String = "gateway-service-addresses"
}

private[persistence] case class GatewayServiceAddress(name: String, host: String, port: Int) extends PersistenceArtifact {
  val kind: String = GatewayServiceAddress.kind
}

private[persistence] object GatewayDeploymentStatus {
  val kind: String = "gateway-deployment-statuses"
}

private[persistence] case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends PersistenceArtifact {
  val kind: String = GatewayDeploymentStatus.kind
}

private[persistence] object RouteTargets {
  val kind: String = "route-targets"
}

private[persistence] case class RouteTargets(name: String, targets: List[RouteTarget]) extends PersistenceArtifact {
  val kind: String = RouteTargets.kind
}

private[persistence] object InternalGateway {
  val kind: String = "internal-gateways"
}

private[persistence] case class InternalGateway(gateway: Gateway) extends PersistenceArtifact {
  val name: String = gateway.name

  val kind: String = InternalGateway.kind
}
