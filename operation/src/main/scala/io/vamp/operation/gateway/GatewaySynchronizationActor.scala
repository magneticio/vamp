package io.vamp.operation.gateway

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.{Config, Namespace, RootAnyMap}
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.container_driver.ContainerDriverActor.DeployedGateways
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.GatewayDriverActor.{Pull, Push}
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.ArtifactSupport
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

import scala.util.{Failure, Success}
import scala.concurrent.duration._
class GatewaySynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[GatewaySynchronizationActor] ! SynchronizeAll
}

object GatewaySynchronizationActor {

  val timeout = Config.timeout("vamp.operation.gateway.response-timeout")

  def portRangeLower()(implicit namespace: Namespace): Int = {
    val portRange = Config.string("vamp.operation.gateway.port-range")().split("-").map(_.toInt)
    portRange(0)
  }

  def portRangeUpper()(implicit namespace: Namespace): Int = {
    val portRange = Config.string("vamp.operation.gateway.port-range")().split("-").map(_.toInt)
    portRange(1)
  }

  sealed trait GatewayMessages

  object SynchronizeAll extends GatewayMessages

  case class Synchronize(gateways: List[Gateway], deployments: List[Deployment], marshalled: List[Gateway]) extends GatewayMessages

}

private case class GatewayPipeline(deployable: List[Gateway], nonDeployable: List[Gateway]) {
  val all = deployable ++ nonDeployable
}

class GatewaySynchronizationActor extends CommonSupportForActors with ArtifactSupport with OperationNotificationProvider
    with VampJsonFormats {

  import GatewaySynchronizationActor._

  private var currentPort = portRangeLower - 1

  def receive = {
    case SynchronizeAll ⇒ synchronize()
    case s: Synchronize ⇒ synchronize(s.gateways, s.deployments, s.marshalled)
    case _              ⇒
  }

  private def synchronize() = {
    val sendTo = self
    implicit val timeout = Timeout(5.second)
    (for {
      gateways ← VampPersistence().getAll[Gateway]().map(_.response)
      deployments ← VampPersistence().getAll[Deployment]().map(_.response)
      marshalled ← checked[List[Gateway]](IoC.actorFor[GatewayDriverActor] ? Pull)
    } yield (gateways, deployments, marshalled)) onComplete {
      case Success((gateways, deployments, marshalled)) ⇒ sendTo ! Synchronize(gateways, deployments, marshalled)
      case Failure(error)                               ⇒ reportException(InternalServerError(error))
    }
  }

  private def synchronize(gateways: List[Gateway], deployments: List[Deployment], marshalled: List[Gateway]) = {
    (portAssignment(deployments) andThen instanceUpdate(deployments) andThen select(marshalled) andThen flush)(gateways)
  }

  private def portAssignment(deployments: List[Deployment]): List[Gateway] ⇒ GatewayPipeline = { gateways ⇒
    val used = gateways.map(_.port.number).toSet

    def availablePort = {
      currentPort += 1
      while (used.contains(currentPort)) currentPort += 1
      if (currentPort > portRangeUpper)
        throwException(NoAvailablePortError(portRangeLower, portRangeUpper))
      currentPort
    }

    val (noPortGateways, otherGateways) = gateways.partition { gateway ⇒ !gateway.port.assigned }

    noPortGateways foreach { gateway ⇒
      if (gateway.deployed) VampPersistence().update[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway), _.copy(deployed = false))
      val newPort = if (!gateway.port.assigned) {
        gateway.port.copy(number = availablePort) match {
          case p ⇒ p.copy(value = Option(p.toValue))
        }
      }
      else gateway.port
      for {
        _ <- VampPersistence().update[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway), g ⇒ g.copy(port = newPort))
        finalGateway <- VampPersistence().read[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway))
        _ <- VampPersistence().updateGatewayForDeployment(finalGateway)
      } yield ()
    }

    GatewayPipeline(otherGateways, noPortGateways)
  }

  private def instanceUpdate(deployments: List[Deployment]): GatewayPipeline ⇒ GatewayPipeline = { pipeline ⇒

    val (passThrough, withoutRoutes) = pipeline.deployable.map { gateway ⇒
      val routes = gateway.routes.map {
        case route: DefaultRoute ⇒
          val routeTargets = targets(pipeline.deployable, deployments, route)
          route.copy(targets = routeTargets)
        case route ⇒ route
      }
      val updatedGateway = gateway.copy(routes = routes)
      if(updatedGateway != gateway) {
        VampPersistence().update[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway), _ ⇒ updatedGateway).flatMap(_ =>
          VampPersistence().updateGatewayForDeployment(updatedGateway)
        )
      } else ()
      updatedGateway
    } partition { gateway ⇒
      gateway.routes.forall {
        case route: DefaultRoute if route.targets.nonEmpty ⇒ targets(pipeline.deployable, deployments, route) == route.targets
        case _ ⇒ false
      } || !gateway.internal
    }

    passThrough filter (!_.deployed) foreach { gateway ⇒
      val newGateway = gateway.copy(deployed = true)
      VampPersistence().update[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway), _ => newGateway).flatMap(_ =>
        VampPersistence().updateGatewayForDeployment(newGateway)
      )
    }

    withoutRoutes filter (_.deployed) foreach { gateway ⇒
      val newGateway = gateway.copy(deployed = false)
      VampPersistence().update[Gateway](gatewaySerilizationSpecifier.idExtractor(gateway), _ => newGateway).flatMap(_ =>
        VampPersistence().updateGatewayForDeployment(newGateway)
      )
    }

    GatewayPipeline(passThrough, pipeline.nonDeployable ++ withoutRoutes)
  }

  private def targets(gateways: List[Gateway], deployments: List[Deployment], route: DefaultRoute): List[RouteTarget] = {
    route.path.external match {
      case Some(external) ⇒ ExternalRouteTarget(external, RootAnyMap.empty) :: Nil
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
            gateways.find { gateway ⇒
              gateway.name == route.path.normalized && GatewayPath(gateway.name).segments.head == deployment
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
              _.portBy(port)
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
                  {
                    if (!instance.ports.contains(port))
                      log.error(s"$port does not exist in instance: ${instance.name} host: ${instance.host} ports: ${instance.ports}")
                    Option {
                      InternalRouteTarget(instance.name, Option(instance.host), instance.ports(port))
                    }
                  }
              }
            }.getOrElse(Nil)

          case _ ⇒ None :: Nil
        }

        if (targets.exists(_.isEmpty)) Nil else targets.flatten
    }
  }

  private def select(marshalled: List[Gateway]): GatewayPipeline ⇒ List[Gateway] = { pipeline ⇒

    val selected = pipeline.deployable

    val currentAsMap = marshalled.map(g ⇒ g.name → g).toMap
    val selectedAsMap = selected.map(g ⇒ g.name → g).toMap

    currentAsMap.keySet.diff(selectedAsMap.keySet).foreach(name ⇒ sendEvent(currentAsMap(name), "undeployed"))
    selectedAsMap.keySet.diff(currentAsMap.keySet).foreach(name ⇒ sendEvent(selectedAsMap(name), "deployed"))

    selected
  }

  private def flush: List[Gateway] ⇒ Unit = { gateways ⇒

    val sorted = gateways sortWith { (gateway1, gateway2) ⇒
      val len1 = GatewayPath(gateway1.name).segments.size
      val len2 = GatewayPath(gateway2.name).segments.size
      if (len1 == len2) gateway1.name.compareTo(gateway2.name) < 0
      else len1 < len2
    }

    IoC.actorFor[GatewayDriverActor] ! Push(sorted)
    IoC.actorFor[ContainerDriverActor] ! DeployedGateways(sorted)
  }

  private def sendEvent(gateway: Gateway, event: String) = {
    log.info(s"Gateway event: ${gateway.name} - $event")
    val tags = Set(s"gateways${Event.tagDelimiter}${gateway.name}", event)
    IoC.actorFor[PulseActor] ! Publish(Event(tags, gateway))
  }
}
