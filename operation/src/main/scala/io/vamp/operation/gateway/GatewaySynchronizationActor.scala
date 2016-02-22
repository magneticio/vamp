package io.vamp.operation.gateway

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.GatewayDriverActor.Commit
import io.vamp.model.artifact._
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.db.PersistenceActor.{ Create, Update }
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.operation.{ RouteTargets, GatewayDeploymentStatus, GatewayPort }

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

class GatewaySynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import GatewaySynchronizationActor._

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
      case Failure(error)                   ⇒ throwException(InternalServerError(error))
    }
  }

  private def synchronize(gateways: List[Gateway], deployments: List[Deployment]) = {
    (portAssignment(deployments) andThen instanceUpdate(deployments) andThen flush)(gateways)
  }

  private def portAssignment(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒

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

    otherGateways
  }

  private def instanceUpdate(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒

    val (withRoutes, withoutRoutes) = gateways partition { gateway ⇒
      gateway.routes.forall {
        case route: DefaultRoute if route.targets.nonEmpty ⇒ targets(gateways, deployments, route) == route.targets
        case _ ⇒ false
      }
    }

    withoutRoutes flatMap (_.routes) foreach {
      case route: DefaultRoute ⇒ IoC.actorFor[PersistenceActor] ! Update(RouteTargets(route.path.normalized, targets(gateways, deployments, route)))
      case _                   ⇒
    }

    withRoutes foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! Update(GatewayDeploymentStatus(gateway.name, deployed = true)) }

    withoutRoutes foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! Update(GatewayDeploymentStatus(gateway.name, deployed = false)) }

    withRoutes
  }

  private def targets(gateways: List[Gateway], deployments: List[Deployment], route: DefaultRoute): List[RouteTarget] = {

    val targets = route.path.segments match {

      case reference :: Nil ⇒
        gateways.find {
          _.name == reference
        }.flatMap { gw ⇒
          Option {
            RouteTarget(reference, gw.port.number)
          }
        } :: Nil

      case deployment :: _ :: Nil ⇒
        deployments.find {
          _.name == deployment
        }.flatMap {
          _.gateways.find(_.name == route.path.normalized)
        }.flatMap { gateway ⇒
          Option {
            RouteTarget(route.path.normalized, gateway.port.number)
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
          if (port != 0) Option(RouteTarget(route.path.normalized, port)) else None
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
                RouteTarget(instance.name, instance.host, instance.ports.get(port).get)
              }
          }
        }.getOrElse(Nil)

      case _ ⇒ None :: Nil
    }

    if (targets.exists(_.isEmpty)) Nil else targets.flatten
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
}
