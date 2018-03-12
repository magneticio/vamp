package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.Artifact
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.{ DefaultRoute, _ }

trait GatewayPersistenceMessages {

  case class CreateGatewayServiceAddress(gateway: Gateway, host: String, port: Int) extends PersistenceActor.PersistenceMessages

  case class CreateGatewayPort(gateway: Gateway, port: Int) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayDeploymentStatus(gateway: Gateway, deployed: Boolean) extends PersistenceActor.PersistenceMessages

  case class UpdateGatewayRouteTargets(gateway: Gateway, route: DefaultRoute, targets: List[RouteTarget]) extends PersistenceActor.PersistenceMessages

  case class DeleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages
}

trait GatewayPersistenceOperations extends PersistenceApi {
  this: NotificationProvider with PatchPersistenceOperations ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: CreateGatewayServiceAddress   ⇒ patchGatewayServiceAddress(o.gateway.name, o.host, o.port)

    case o: CreateGatewayPort             ⇒ patchGatewayPort(o.gateway.name, o.port)

    case o: UpdateGatewayDeploymentStatus ⇒ patch(o.gateway.name, g ⇒ g.copy(deployed = o.deployed))

    case o: UpdateGatewayRouteTargets     ⇒ patchGatewayRouteTargets(o.gateway, o.route, o.targets)

    case o: DeleteGatewayRouteTargets     ⇒ deleteGatewayRouteTargets(o.deployment, o.cluster, o.service)
  }

  override protected def interceptor[T <: Artifact](set: Boolean): PartialFunction[T, T] = {
    case gateway: Gateway ⇒ get(gateway).map(old ⇒ intercept(set)(gateway, old)).getOrElse(gateway).asInstanceOf[T]
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

  private def patch(name: String, using: Gateway ⇒ Gateway): Unit = {
    get(name, classOf[Gateway]) match {
      case Some(g) ⇒
        val ng = using(g)
        val modified = ng != g
        if (modified) interceptForDeployment(ng, update = true)
        replyUpdate(ng, modified)
      case None ⇒ replyNone()
    }
  }

  private def intercept(set: Boolean)(newGateway: Gateway, oldGateway: Gateway): Gateway = {
    if (set) {
      val gateway = interceptForGateway(newGateway, oldGateway)
      interceptForDeployment(gateway, set)
      gateway
    }
    else {
      interceptForDeployment(newGateway, set)
      newGateway
    }
  }

  private def interceptForGateway(newGateway: Gateway, oldGateway: Gateway): Gateway = {
    val targets = oldGateway.routes.map {
      case r: DefaultRoute ⇒ r.path.normalized → r.targets
      case r               ⇒ r.path.normalized → Nil
    }.toMap
    val routes = newGateway.routes.map {
      case r: DefaultRoute ⇒ r.copy(targets = targets.getOrElse(r.path.normalized, Nil))
      case r               ⇒ r
    }
    newGateway.copy(
      service = oldGateway.service,
      routes = routes
    )
  }

  private def interceptForDeployment(gateway: Gateway, update: Boolean): Unit = {
    (GatewayPath(gateway.name).segments match {
      case d :: c :: p :: Nil ⇒
        get(d, classOf[Deployment]).flatMap { deployment ⇒
          deployment.clusters.find(_.name == c).map(cluster ⇒ (deployment, cluster, p))
        }
      case _ ⇒ None
    }).foreach {
      case (deployment, cluster, port) ⇒
        set(deployment.copy(clusters = deployment.clusters.map {
          case c if c.name == cluster.name ⇒
            if (update) {
              cluster.copy(gateways = cluster.gateways.map {
                case g if g.name == gateway.name ⇒ gateway.copy(port = gateway.port.copy(name = port))
                case g                           ⇒ g
              })
            }
            else cluster.copy(gateways = cluster.gateways.filterNot(_.name == gateway.name))
          case c ⇒ c
        }))
    }
  }

  private def deleteGatewayRouteTargets(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    val name = GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
    patch(name, { g ⇒
      val routes = g.routes.map {
        case r: DefaultRoute ⇒ r.copy(targets = Nil)
        case r               ⇒ r
      }
      g.copy(routes = routes)
    })
  }
}
