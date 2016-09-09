package io.vamp.persistence.db

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact.{ DefaultRoute, _ }

import scala.concurrent.Future

object GatewayPersistenceMessages extends GatewayPersistenceMessages

trait GatewayPersistenceMessages {

  case class CreateInnerGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayServiceAddress(gateway: Gateway, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayPort(gateway: Gateway, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayServiceAddress(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayDeploymentStatus(gateway: Gateway, deployed: Boolean) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayRouteTargets(route: DefaultRoute, targets: List[RouteTarget]) extends PersistenceActor.PersistenceMessages

  case class UpdateInnerGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class DeleteInnerGateway(name: String) extends PersistenceActor.PersistenceMessages

  case class DeleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port) extends PersistenceActor.PersistenceMessages

  case class ResetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

}

trait GatewayPersistenceOperations {
  this: CommonSupportForActors ⇒

  import DevelopmentPersistenceOperations._
  import GatewayPersistenceMessages._

  implicit def timeout: Timeout

  protected def receiveGateway: Actor.Receive = {

    case o: CreateInnerGateway            ⇒ createInnerGateway(o.gateway)

    case o: CreateGatewayServiceAddress   ⇒ createGatewayServiceAddress(o.gateway, o.host, o.port)

    case o: CreateGatewayPort             ⇒ createGatewayPort(o.gateway, o.port)

    case o: UpdateGatewayServiceAddress   ⇒ updateGatewayServiceAddress(o.deployment, o.cluster, o.service, o.host, o.port)

    case o: UpdateGatewayDeploymentStatus ⇒ updateGatewayDeploymentStatus(o.gateway, o.deployed)

    case o: UpdateGatewayRouteTargets     ⇒ updateGatewayRouteTargets(o.route, o.targets)

    case o: UpdateInnerGateway            ⇒ updateInnerGateway(o.gateway)

    case o: DeleteInnerGateway            ⇒ deleteInnerGateway(o.name)

    case o: DeleteGatewayRouteTargets     ⇒ deleteGatewayRouteTargets(o.deployment, o.cluster, o.service, o.port)

    case o: ResetGateway                  ⇒ resetGateway(o.deployment, o.cluster, o.service)
  }

  private def createInnerGateway(gateway: Gateway) = reply {
    self ? PersistenceActor.Create(InnerGateway(gateway))
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

  private def updateInnerGateway(gateway: Gateway) = reply {
    self ? PersistenceActor.Update(InnerGateway(gateway))
  }

  private def deleteInnerGateway(name: String) = reply {
    self ? PersistenceActor.Delete(name, classOf[InnerGateway])
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
      PersistenceActor.Delete(name, classOf[InnerGateway]) :: Nil

    Future.sequence(messages.map(self ? _))
  }
}

private[persistence] case class GatewayPort(name: String, port: Int) extends Artifact {
  val kind = "gateway-port"
}

private[persistence] case class GatewayServiceAddress(name: String, host: String, port: Int) extends Artifact {
  val kind = "gateway-service-address"
}

private[persistence] case class GatewayDeploymentStatus(name: String, deployed: Boolean) extends Artifact {
  val kind = "gateway-deployment-status"
}

private[persistence] case class RouteTargets(name: String, targets: List[RouteTarget]) extends Artifact {
  val kind = "route-targets"
}

private[persistence] case class InnerGateway(gateway: Gateway) extends Artifact {
  val name = gateway.name

  val kind = "inner-gateway"
}
