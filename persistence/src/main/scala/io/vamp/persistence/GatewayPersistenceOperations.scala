package io.vamp.persistence

import akka.actor.Actor
import io.vamp.model.artifact.{ DefaultRoute, _ }

trait GatewayPersistenceMessages {

  case class CreateInternalGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayServiceAddress(gateway: Gateway, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayPort(gateway: Gateway, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayServiceAddress(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayDeploymentStatus(gateway: Gateway, deployed: Boolean) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayRouteTargets(gateway: Gateway, route: DefaultRoute, targets: List[RouteTarget]) extends PersistenceActor.PersistenceMessages

  case class UpdateInternalGateway(gateway: Gateway) extends PersistenceActor.PersistenceMessages

  case class DeleteInternalGateway(name: String) extends PersistenceActor.PersistenceMessages

  case class DeleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

  case class ResetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

}

trait GatewayPersistenceOperations {
  this: PersistenceApi with PatchPersistenceOperations ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: CreateInternalGateway         ⇒ patchInternalGateway(o.gateway)

    case o: CreateGatewayServiceAddress   ⇒ patchGatewayServiceAddress(o.gateway.name, o.host, o.port)

    case o: CreateGatewayPort             ⇒ patchGatewayPort(o.gateway.name, o.port)

    case o: UpdateGatewayServiceAddress   ⇒ patchGatewayServiceAddress(serviceArtifactName(o.deployment, o.cluster, o.service), o.host, o.port)

    case o: UpdateGatewayDeploymentStatus ⇒ patch(o.gateway.name, g ⇒ g.copy(deployed = o.deployed))

    case o: UpdateGatewayRouteTargets     ⇒ patchGatewayRouteTargets(o.gateway, o.route, o.targets)

    case o: UpdateInternalGateway         ⇒ patchInternalGateway(o.gateway)

    case o: DeleteInternalGateway         ⇒ deleteInternalGateway(o.name)

    case o: DeleteGatewayRouteTargets     ⇒ deleteGatewayRouteTargets(serviceArtifactName(o.deployment, o.cluster, o.service))

    case o: ResetGateway                  ⇒ resetGateway(o.deployment, o.cluster, o.service)
  }

  private def resetGateway(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    var modified = false
    val name = GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
    get(deployment).map { d ⇒
      d.copy(clusters = d.clusters.map {
        case c if c.name == cluster.name ⇒
          val gateways = {
            val ng = c.gateways.filterNot(_.name == name)
            modified = ng != c.gateways
            ng
          }
          c.copy(gateways = gateways)
        case c ⇒ c
      })
    } foreach { d ⇒ replyUpdate(d, modified) }
  }

  private def patchInternalGateway(gateway: Gateway): Unit = {
    deploymentCluster(gateway.name) match {
      case Some((dName, cName)) ⇒
        get(dName, classOf[Deployment]) match {
          case Some(d) ⇒
            var modified = false
            d.copy(clusters = d.clusters.map {
              case c if c.name == cName ⇒
                val gateways = {
                  c.gateways.filter(_.routes.nonEmpty).map {
                    case g if g.name == gateway.name ⇒
                      val ng = g.copy(port = g.port.copy(name = gateway.port.name))
                      modified = ng != g
                      ng
                    case g ⇒ g
                  }
                }
                c.copy(gateways = gateways)
              case c ⇒ c
            })
            replyUpdate(d, modified)
          case None ⇒ replyUpdate(gateway, update = true)
        }
      case None ⇒ replyUpdate(gateway, update = true)
    }
  }

  private def deleteInternalGateway(name: String): Unit = {
    deploymentCluster(name).foreach {
      case (dName, cName) ⇒
        var modified = false
        get(dName, classOf[Deployment]).map { d ⇒
          d.copy(clusters = d.clusters.map {
            case c if c.name == cName ⇒
              val gateways = c.gateways.filterNot(_.name == name)
              modified = gateways != c.gateways
              c.copy(gateways = gateways)
            case c ⇒ c
          })
        } foreach { d ⇒ replyUpdate(d, modified) }
    }
  }

  private def patchGatewayServiceAddress(name: String, host: String, port: Int): Unit = {
    patch(name, { g ⇒
      g.copy(service = Option(GatewayService(host, g.port.copy(number = port) match { case p ⇒ p.copy(value = Option(p.toValue)) })))
    })
  }

  private def patchGatewayPort(name: String, port: Int): Unit = {
    patch(name, { g ⇒
      g.copy(port = {
        if (!g.port.assigned) {
          g.port.copy(number = port) match {
            case p ⇒ p.copy(value = Option(p.toValue))
          }
        }
        else g.port
      })
    })
  }

  private def patchGatewayRouteTargets(gateway: Gateway, route: DefaultRoute, targets: List[RouteTarget]): Unit = {
    patch(gateway.name, { g ⇒
      val routes = g.routes.map {
        case r: DefaultRoute if r.path.normalized == route.path.normalized ⇒
          r.copy(targets = targets)
        case r ⇒ r
      }
      g.copy(routes = routes)
    })
  }

  private def deleteGatewayRouteTargets(name: String): Unit = {
    patch(name, { g ⇒
      val routes = g.routes.map {
        case r: DefaultRoute ⇒ r.copy(targets = Nil)
        case r               ⇒ r
      }
      g.copy(routes = routes)
    })
  }

  private def patch(name: String, using: Gateway ⇒ Gateway): Unit = {
    var modified = false
    get(name, classOf[Gateway]).map { g ⇒
      val ng = using(g)
      modified = ng != g
      ng
    } foreach { g ⇒ replyUpdate(g, modified) }
  }

  private def serviceArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): String = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
  }

  private def deploymentCluster(name: String): Option[(String, String)] = {
    GatewayPath(name).segments match {
      case d :: c :: _ :: Nil ⇒ Option(d → c)
      case _                  ⇒ None
    }
  }
}
