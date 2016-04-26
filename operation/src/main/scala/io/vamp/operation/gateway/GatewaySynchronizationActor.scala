package io.vamp.operation.gateway

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.GatewayDriverActor.Commit
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.db.PersistenceActor.{ Create, Update }
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.operation.{ GatewayDeploymentStatus, GatewayPort, RouteTargets }
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class GatewaySynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[GatewaySynchronizationActor] ! SynchronizeAll
}

object GatewaySynchronizationActor {

  val configuration = ConfigFactory.load().getConfig("vamp.operation.gateway")

  val timeout = Timeout(configuration.getInt("response-timeout") seconds)

  val (portRangeLower: Int, portRangeUpper: Int) = {
    val portRange = configuration.getString("port-range").split("-").map(_.toInt)
    (portRange(0), portRange(1))
  }

  sealed trait GatewayMessages

  object SynchronizeAll extends GatewayMessages

  case class Synchronize(gateways: List[Gateway], deployments: List[Deployment]) extends GatewayMessages

}

private case class GatewayPipeline(deployable: List[Gateway], nonDeployable: List[Gateway]) {
  val all = deployable ++ nonDeployable
}

class GatewaySynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import GatewaySynchronizationActor._

  // TODO current gateways should be retrieved from key-value store
  private var current: List[Gateway] = Nil

  def receive = {
    case SynchronizeAll                     ⇒ synchronize()
    case Synchronize(gateways, deployments) ⇒ synchronize(gateways, deployments)
    case _                                  ⇒
  }

  private def synchronize() = {
    val sendTo = self
    implicit val timeout = PersistenceActor.timeout
    (for {
      gateways ← allArtifacts[Gateway]
      deployments ← allArtifacts[Deployment]
    } yield (gateways, deployments)) onComplete {
      case Success((gateways, deployments)) ⇒ sendTo ! Synchronize(gateways, deployments)
      case Failure(error)                   ⇒ reportException(InternalServerError(error))
    }
  }

  private def synchronize(gateways: List[Gateway], deployments: List[Deployment]) = {
    (portAssignment(deployments) andThen instanceUpdate(deployments) andThen select andThen flush)(gateways)
  }

  private def portAssignment(deployments: List[Deployment]): List[Gateway] ⇒ GatewayPipeline = { gateways ⇒

    var currentPort = portRangeLower - 1
    val used = gateways.map(_.port.number).toSet

    def availablePort = {
      currentPort += 1

      while (used.contains(currentPort) && currentPort < portRangeUpper) currentPort += 1

      if (currentPort == portRangeUpper)
        reportException(NoAvailablePortError(portRangeLower, portRangeUpper))

      currentPort
    }

    val (noPortGateways, otherGateways) = gateways.partition { gateway ⇒ !gateway.port.assigned }

    noPortGateways foreach { gateway ⇒
      IoC.actorFor[PersistenceActor] ! Update(GatewayDeploymentStatus(gateway.name, deployed = false))
      IoC.actorFor[PersistenceActor] ! Create(GatewayPort(gateway.name, availablePort))
    }

    GatewayPipeline(otherGateways, noPortGateways)
  }

  private def instanceUpdate(deployments: List[Deployment]): GatewayPipeline ⇒ GatewayPipeline = { pipeline ⇒

    val (passThrough, withoutRoutes) = pipeline.deployable.map { gateway ⇒
      val routes = gateway.routes.map {
        case route: DefaultRoute ⇒
          val routeTargets = targets(pipeline.deployable, deployments, route)
          val targetMatch = routeTargets == route.targets
          if (!targetMatch) IoC.actorFor[PersistenceActor] ! Update(RouteTargets(route.path.normalized, routeTargets))
          route.copy(targets = routeTargets)
        case route ⇒ route
      }

      gateway.copy(routes = routes)
    } partition { gateway ⇒
      gateway.routes.forall {
        case route: DefaultRoute if route.targets.nonEmpty ⇒ targets(pipeline.deployable, deployments, route) == route.targets
        case _ ⇒ false
      } || !gateway.inner
    }

    passThrough foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! Update(GatewayDeploymentStatus(gateway.name, deployed = true)) }

    withoutRoutes foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! Update(GatewayDeploymentStatus(gateway.name, deployed = false)) }

    GatewayPipeline(passThrough, pipeline.nonDeployable ++ withoutRoutes)
  }

  private def targets(gateways: List[Gateway], deployments: List[Deployment], route: DefaultRoute): List[RouteTarget] = {
    route.path.external match {
      case Some(external) ⇒ ExternalRouteTarget(external) :: Nil
      case _ ⇒

        val targets = route.path.segments match {

          case reference :: Nil ⇒
            gateways.find {
              _.name == reference
            }.flatMap { gw ⇒
              Option {
                InternalRouteTarget(reference, gw.port.number)
              }
            } :: Nil

          case deployment :: _ :: Nil ⇒
            deployments.find {
              _.name == deployment
            }.flatMap {
              _.gateways.find(_.name == route.path.normalized)
            }.flatMap { gateway ⇒
              Option {
                InternalRouteTarget(route.path.normalized, gateway.port.number)
              }
            } :: Nil

          case deployment :: cluster :: port :: Nil ⇒
            deployments.find {
              _.name == deployment
            }.flatMap {
              _.clusters.find(_.name == cluster)
            }.flatMap {
              _.portMapping.get(port)
            }.flatMap { port ⇒
              if (port != 0) Option(InternalRouteTarget(route.path.normalized, port)) else None
            } :: Nil

          case deployment :: cluster :: service :: port :: Nil ⇒
            deployments.find {
              _.name == deployment
            }.flatMap {
              _.clusters.find(_.name == cluster)
            }.flatMap {
              _.services.find(_.breed.name == service)
            }.map { service ⇒
              service.instances.map {
                instance ⇒
                  Option {
                    InternalRouteTarget(instance.name, instance.host, instance.ports.get(port).get)
                  }
              }
            }.getOrElse(Nil)

          case _ ⇒ None :: Nil
        }

        if (targets.exists(_.isEmpty)) Nil else targets.flatten
    }
  }

  private def select: GatewayPipeline ⇒ List[Gateway] = { pipeline ⇒

    def byDeploymentName(gateways: List[Gateway]) = gateways.filter(_.inner).groupBy(gateway ⇒ GatewayPath(gateway.name).segments.head)

    val currentByDeployment = byDeploymentName(current)
    val deployable = byDeploymentName(pipeline.deployable)
    val nonDeployable = byDeploymentName(pipeline.nonDeployable)

    val inner = byDeploymentName(pipeline.all).toList.flatMap {
      case (d, g) if deployable.contains(d) && !nonDeployable.contains(d) ⇒ g
      case (d, g) if nonDeployable.contains(d) ⇒ currentByDeployment.getOrElse(d, Nil)
      case (_, g) ⇒ g
    }

    val selected = pipeline.deployable.filterNot(_.inner) ++ inner

    val currentAsMap = current.map(g ⇒ g.name -> g).toMap
    val selectedAsMap = selected.map(g ⇒ g.name -> g).toMap

    currentAsMap.keySet.diff(selectedAsMap.keySet).foreach(name ⇒ publishUndeployed(currentAsMap.get(name).get))
    selectedAsMap.keySet.diff(currentAsMap.keySet).foreach(name ⇒ publishDeployed(selectedAsMap.get(name).get))

    current = selected
    current
  }

  private def flush: List[Gateway] ⇒ Unit = { gateways ⇒
    IoC.actorFor[GatewayDriverActor] ! Commit {
      gateways sortWith { (gateway1, gateway2) ⇒
        val len1 = GatewayPath(gateway1.name).segments.size
        val len2 = GatewayPath(gateway2.name).segments.size
        if (len1 == len2) gateway1.name.compareTo(gateway2.name) < 0
        else len1 < len2
      }
    }
  }

  private def publishDeployed(gateway: Gateway) = sendEvent(gateway, "deployed")

  private def publishUndeployed(gateway: Gateway) = sendEvent(gateway, "undeployed")

  private def sendEvent(gateway: Gateway, event: String) = {
    log.info(s"Gateway event: ${gateway.name} - $event")
    val tags = Set(s"gateways${Event.tagDelimiter}${gateway.name}", event)
    IoC.actorFor[PulseActor] ! Publish(Event(tags, gateway), publishEventValue = true)
  }
}
