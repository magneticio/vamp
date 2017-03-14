package io.vamp.persistence

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact.{ DefaultRoute, _ }

import scala.concurrent.Future

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
  this: CommonSupportForActors ⇒

  import DeploymentPersistenceOperations._
  import PersistenceActor._

  implicit def timeout: Timeout

  def receive: Actor.Receive = {

    case o: CreateInternalGateway         ⇒ createInternalGateway(o.gateway)

    case o: CreateGatewayServiceAddress   ⇒ createGatewayServiceAddress(o.gateway, o.host, o.port)

    case o: CreateGatewayPort             ⇒ createGatewayPort(o.gateway, o.port)

    case o: UpdateGatewayServiceAddress   ⇒ updateGatewayServiceAddress(o.deployment, o.cluster, o.service, o.host, o.port)

    case o: UpdateGatewayDeploymentStatus ⇒ updateGatewayDeploymentStatus(o.gateway, o.deployed)

    case o: UpdateGatewayRouteTargets     ⇒ updateGatewayRouteTargets(o.route, o.targets)

    case o: UpdateInternalGateway         ⇒ updateInternalGateway(o.gateway)

    case o: DeleteInternalGateway         ⇒ deleteInternalGateway(o.name)

    case o: DeleteGatewayRouteTargets     ⇒ deleteGatewayRouteTargets(o.deployment, o.cluster, o.service, o.port)

    case o: ResetGateway                  ⇒ resetGateway(o.deployment, o.cluster, o.service)
  }

  private def createInternalGateway(gateway: Gateway) = reply {
    self ? PersistenceActor.Create(InternalGateway(gateway))
  }

  private def createGatewayServiceAddress(gateway: Gateway, host: String, port: Int) = reply {
    self ? PersistenceActor.Create(GatewayServiceAddress(gateway.name, host, port))
  }

  private def createGatewayPort(gateway: Gateway, port: Int) = reply {
    self ? PersistenceActor.Update(GatewayPort(gateway.name, port))
  }

  private def updateGatewayServiceAddress(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, host: String, port: Int) = reply {
    self ? PersistenceActor.Update(GatewayServiceAddress(serviceArtifactName(deployment, cluster, service), host, port))
  }

  private def updateGatewayDeploymentStatus(gateway: Gateway, deployed: Boolean) = reply {
    self ? PersistenceActor.Update(GatewayDeploymentStatus(gateway.name, deployed))
  }

  private def updateGatewayRouteTargets(route: DefaultRoute, targets: List[RouteTarget]) = reply {
    self ? PersistenceActor.Update(RouteTargets(route.path.normalized, targets))
  }

  private def updateInternalGateway(gateway: Gateway) = reply {
    self ? PersistenceActor.Update(InternalGateway(gateway))
  }

  private def deleteInternalGateway(name: String) = reply {
    self ? PersistenceActor.Delete(name, classOf[InternalGateway])
  }

  private def deleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port) = reply {
    self ? PersistenceActor.Delete(servicePortArtifactName(deployment, cluster, service, port), classOf[RouteTargets])
  }

  private def resetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = reply {
    val name = serviceArtifactName(deployment, cluster, service)

    val messages = PersistenceActor.Delete(name, classOf[GatewayPort]) ::
      PersistenceActor.Delete(name, classOf[GatewayServiceAddress]) ::
      PersistenceActor.Delete(name, classOf[GatewayDeploymentStatus]) ::
      PersistenceActor.Delete(name, classOf[RouteTargets]) ::
      PersistenceActor.Delete(name, classOf[InternalGateway]) :: Nil

    Future.sequence(messages.map(self ? _))
  }
}

private[persistence] case class GatewayPort(name: String, port: Int) extends PersistenceArtifact {
  val kind = "gateway-port"
}

private[persistence] case class GatewayServiceAddress(name: String, host: String, port: Int) extends PersistenceArtifact {
  val kind = "gateway-service-address"
}

private[persistence] case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends PersistenceArtifact {
  val kind = "gateway-deployment-status"
}

private[persistence] case class RouteTargets(name: String, targets: List[RouteTarget]) extends PersistenceArtifact {
  val kind = "route-targets"
}

private[persistence] case class InternalGateway(gateway: Gateway) extends PersistenceArtifact {
  val name = gateway.name

  val kind = "internal-gateway"
}
