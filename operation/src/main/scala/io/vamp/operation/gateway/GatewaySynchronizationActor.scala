package io.vamp.operation.gateway

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.common.util.HashUtil
import io.vamp.common.{ Config, ConfigMagnet, Namespace, NamespaceProvider }
import io.vamp.container_driver.ContainerDriverActor.{ DeployedGateways, GetRoutingGroups }
import io.vamp.container_driver.{ ContainerDriverActor, RoutingGroup }
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.GatewayDriverActor.{ Pull, Push }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.notification.InvalidSelectorError
import io.vamp.model.reader.{ NameValidator, Percentage }
import io.vamp.model.resolver.{ ConfigurationValueResolver, ValueResolver }
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

import scala.util.{ Failure, Success, Try }

class GatewaySynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick(): Unit = IoC.actorFor[GatewaySynchronizationActor] ! SynchronizeAll
}

object GatewaySynchronizationActor {

  val selector: ConfigMagnet[String] = Config.string("vamp.operation.gateway.selector")

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

  case class Synchronize(gateways: List[Gateway], deployments: List[Deployment], routingGroups: List[RoutingGroup], marshalled: List[Gateway]) extends GatewayMessages

}

trait GatewaySelectorResolver extends ValueResolver {
  this: NotificationProvider ⇒

  def defaultSelector()(implicit namespace: Namespace): String = {
    val ns = namespace
    resolve(
      GatewaySynchronizationActor.selector(), new ConfigurationValueResolver with NamespaceProvider {
        override implicit def namespace: Namespace = ns
      }.valueForReference orElse PartialFunction[ValueReference, String] {
        case LocalReference("namespace") ⇒ s"$$namespace"
        case _                           ⇒ ""
      }
    )
  }
}

private case class GatewayPipeline(deployable: List[Gateway], nonDeployable: List[Gateway]) {
  val all: List[Gateway] = deployable ++ nonDeployable
}

class GatewaySynchronizationActor extends CommonSupportForActors with GatewaySelectorResolver with NameValidator with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import GatewaySynchronizationActor._
  import PersistenceActor._

  private var currentPort = portRangeLower - 1
  private val selector: Option[RouteSelector] = {
    Try(RouteSelector(defaultSelector()).verified).toOption match {
      case Some(s) ⇒ Option(s)
      case None ⇒
        reportException(InvalidSelectorError(defaultSelector()))
        None
    }
  }

  def receive: Actor.Receive = {
    case SynchronizeAll ⇒ synchronize()
    case s: Synchronize ⇒ synchronize(s.gateways, s.deployments, s.routingGroups, s.marshalled)
    case _              ⇒
  }

  private def synchronize(): Unit = {
    val sendTo = self
    implicit val timeout: Timeout = PersistenceActor.timeout()
    (for {
      gateways ← consume(allArtifacts[Gateway])
      deployments ← consume(allArtifacts[Deployment])
      routingGroups ← checked[List[RoutingGroup]](IoC.actorFor[ContainerDriverActor] ? GetRoutingGroups)
      marshalled ← checked[List[Gateway]](IoC.actorFor[GatewayDriverActor] ? Pull)
    } yield (gateways, deployments, routingGroups, marshalled)) onComplete {
      case Success((gateways, deployments, routingGroups, marshalled)) ⇒ sendTo ! Synchronize(gateways, deployments, routingGroups, marshalled)
      case Failure(error) ⇒ reportException(InternalServerError(error))
    }
  }

  private def synchronize(gateways: List[Gateway], deployments: List[Deployment], routingGroups: List[RoutingGroup], marshalled: List[Gateway]): Unit = {
    (portAssignment(deployments) andThen instanceUpdate(deployments, routingGroups) andThen select(marshalled) andThen flush)(gateways)
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
      if (gateway.deployed) IoC.actorFor[PersistenceActor] ! UpdateGatewayDeploymentStatus(gateway, deployed = false)
      IoC.actorFor[PersistenceActor] ! CreateGatewayPort(gateway, availablePort)
    }

    GatewayPipeline(otherGateways, noPortGateways)
  }

  private def instanceUpdate(deployments: List[Deployment], routingGroups: List[RoutingGroup]): GatewayPipeline ⇒ GatewayPipeline = { pipeline ⇒
    val (passThrough, withoutRoutes) = pipeline.deployable.map { gateway ⇒
      routes(gateway, deployments, routingGroups, pipeline)
    } partition { gateway ⇒
      gateway.selector.isDefined || gateway.routes.exists {
        case route: DefaultRoute ⇒ route.selector.isDefined || route.targets.nonEmpty
        case _                   ⇒ false
      } || !gateway.internal
    }

    passThrough filter (!_.deployed) foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! UpdateGatewayDeploymentStatus(gateway, deployed = true) }
    withoutRoutes filter (_.deployed) foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! UpdateGatewayDeploymentStatus(gateway, deployed = false) }
    GatewayPipeline(passThrough, pipeline.nonDeployable ++ withoutRoutes)
  }

  private def routes(gateway: Gateway, deployments: List[Deployment], routingGroups: List[RoutingGroup], pipeline: GatewayPipeline): Gateway = {
    gateway.selector match {
      case Some(s) ⇒
        val groups = RouteSelectionProcessor.groups(s, routingGroups, selector).map {
          case (n, v) ⇒ Try(validateName(n)).getOrElse(HashUtil.hexSha1(n)) → v
        }

        val routes = gateway.routes.map(route ⇒ route.path.normalized → route).toMap

        val updated: List[Route] = gateway.routes.filter(route ⇒ groups.contains(route.path.normalized)).map {
          case route: DefaultRoute ⇒ route.copy(targets = groups(route.path.normalized))
          case route               ⇒ route
        }

        val fresh = groups.filterNot {
          case (n, _) ⇒ routes.contains(n)
        }.map {
          case (n, t) ⇒ DefaultRoute(
            name = "",
            metadata = Map("groups" → n, "title" → s"route $n"),
            path = GatewayPath(n),
            selector = None,
            weight = Option(Percentage(0)),
            condition = None,
            conditionStrength = Option(Percentage(0)),
            rewrites = Nil,
            balance = None,
            targets = t
          )
        }

        val availableWeight = 100 - updated.collect { case route: DefaultRoute ⇒ route.weight.map(_.value).getOrElse(0) }.sum

        var all = updated ++ fresh.zipWithIndex.map {
          case (route, index) ⇒
            val calculated = if (index == 0) availableWeight else 0
            route.copy(weight = Option(Percentage(calculated)))
        }

        val total = all.collect { case route: DefaultRoute ⇒ route.weight.map(_.value).getOrElse(0) }.sum

        if (total < 100) {
          var usedWeight = 0
          val factor = 100.0 / total
          val (const, mut) = all.partition {
            case r: DefaultRoute if r.weight.exists(_.value > 0) ⇒ false
            case _ ⇒ true
          }
          all = const ++ mut.zipWithIndex.collect {
            case (route: DefaultRoute, index) ⇒
              val calculated = if (index == fresh.size - 1) 100 - usedWeight else (route.weight.getOrElse(Percentage(0)).value * factor).toInt
              usedWeight += calculated
              route.copy(weight = Option(Percentage(calculated)))
          }
        }

        if (all != gateway.routes) {
          val ng = gateway.copy(routes = all)
          IoC.actorFor[PersistenceActor] ! Update(ng)
          ng
        }
        else gateway

      case None ⇒
        val routes = gateway.routes.map {
          case route: DefaultRoute ⇒
            val routeTargets = route.selector match {
              case Some(s) ⇒ RouteSelectionProcessor.targets(s, routingGroups, selector)
              case _       ⇒ targets(pipeline.deployable, deployments, route)
            }
            val targetMatch = routeTargets == route.targets
            if (!targetMatch) IoC.actorFor[PersistenceActor] ! UpdateGatewayRouteTargets(gateway, route, routeTargets)
            route.copy(targets = routeTargets)
          case route ⇒ route
        }
        gateway.copy(routes = routes)
    }
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
            gateways.find { gateway ⇒
              gateway.name == route.path.normalized && GatewayPath(gateway.name).segments.head == deployment
            }.flatMap { gateway ⇒
              Option {
                InternalRouteTarget(route.path.normalized, gateway.port.number)
              }
            } :: Nil

          case _ :: _ :: _ :: Nil ⇒
            gateways.find { gateway ⇒
              gateway.name == route.path.normalized && gateway.port.number != 0
            }.flatMap { gateway ⇒
              Option {
                InternalRouteTarget(route.path.normalized, gateway.port.number)
              }
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
                  if (!instance.ports.contains(port))
                    log.error(s"$port does not exist in instance: ${instance.name} host: ${instance.host} ports: ${instance.ports}")
                  Option {
                    InternalRouteTarget(instance.name, Option(instance.host), instance.ports(port))
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

  private def sendEvent(gateway: Gateway, event: String): Unit = {
    log.info(s"Gateway event: ${gateway.name} - $event")
    val tags = Set(s"gateways${Event.tagDelimiter}${gateway.name}", event)
    IoC.actorFor[PulseActor] ! Publish(Event(Event.defaultVersion, tags, gateway))
  }
}
