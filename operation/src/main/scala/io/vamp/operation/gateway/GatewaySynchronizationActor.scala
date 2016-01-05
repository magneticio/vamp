package io.vamp.operation.gateway

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.GatewayDriverActor.Commit
import io.vamp.model.artifact._
import io.vamp.model.notification.UnsupportedRoutePathError
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.PersistenceActor.{ Create, Delete, Update }
import io.vamp.persistence.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }

import scala.concurrent.duration._
import scala.language.{ existentials, postfixOps }
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

  trait GatewayMessages

  object SynchronizeAll extends GatewayMessages

}

class GatewaySynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import GatewaySynchronizationActor._

  def receive = {
    case SynchronizeAll ⇒ //synchronize()
    case any            ⇒ //if (sender() != IoC.actorFor[PersistenceActor]) unsupported(UnsupportedGatewayRequest(any))
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    (for {
      gateways ← allArtifacts[Gateway]
      deployments ← allArtifacts[Deployment] map { deployments ⇒ update(gateways, deployments) }
    } yield (gateways, deployments)) onComplete {
      case Success((gateways, deployments)) ⇒ (add(deployments) andThen remove(deployments) andThen flush(deployments))(gateways)
      case Failure(error)                   ⇒ throwException(InternalServerError(error))
    }
  }

  private def update(gateways: List[Gateway], deployments: List[Deployment]): List[Deployment] = {

    var currentPort = portRangeLower - 1
    val used = gateways.map(_.port.number).toSet

    def availablePort = {
      currentPort += 1

      while (used.contains(currentPort) && currentPort < portRangeUpper) currentPort += 1

      if (currentPort == portRangeUpper)
        reportException(NoAvailablePortError(portRangeLower, portRangeUpper))

      currentPort
    }

    deployments.map { deployment ⇒
      val (update, keep) = deployment.clusters.partition { cluster ⇒
        cluster.services.forall(_.state.isDone) && cluster.portMapping.exists { case (_, value) ⇒ value == 0 }
      }

      val updated = update.map { cluster ⇒
        cluster.copy(portMapping = cluster.portMapping.map {
          case (name, value) ⇒ name -> (if (value == 0) availablePort else value)
        })
      }

      val clusters = updated ++ keep

      val deploymentPorts = clusters.flatMap({ cluster ⇒
        cluster.services.map(_.breed).flatMap(_.ports).map({ port ⇒
          Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.portMapping.get(port.name).flatMap(n ⇒ Some(n.toString)))
        })
      }).map(p ⇒ p.name -> p).toMap ++ deployment.ports.map(p ⇒ p.name -> p).toMap

      val updatedDeployment = deployment.copy(clusters = clusters, ports = deploymentPorts.values.toList)

      if (update.nonEmpty) IoC.actorFor[PersistenceActor] ! Update(updatedDeployment)

      updatedDeployment
    }
  }

  private def add(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒
    val deploymentGateways = deployments.flatMap { deployment ⇒

      val clusterGateways = deployment.clusters.filter(_.services.forall(_.state.isDone)).flatMap { cluster ⇒
        cluster.routing.map { routing ⇒
          val port = routing.port.copy(value = cluster.portMapping.get(routing.port.name).flatMap { number ⇒ Port(number, routing.port.`type`).value })
          val routes = routing.routes.flatMap {
            case route: DefaultRoute if route.length == 1 ⇒
              if (cluster.services.exists(_.breed.name == route.path.segments.head))
                route.copy(path = GatewayPath(deployment.name :: cluster.name :: route.path.normalized :: routing.port.name :: Nil)) :: Nil
              else Nil
            case route ⇒ throwException(InternalServerError(s"unsupported cluster route: ${route.length}"))
          }
          routing.copy(port = port, routes = routes)
        }
      }

      deployment.gateways ++ clusterGateways
    }

    deploymentGateways.foreach { gateway ⇒
      if (!gateways.exists(_.name == gateway.name)) {
        log.info(s"gateway created: ${gateway.name}")
        IoC.actorFor[PersistenceActor] ! Create(gateway)
      }
    }

    (gateways ++ deploymentGateways.filterNot { gateway ⇒
      gateways.exists(_.name == gateway.name) && !gateway.inner
    }).map(gateway ⇒ gateway.name -> gateway).toMap.values.toList
  }

  private def remove(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒

    val (remove, keep) = gateways.partition { gateway ⇒
      GatewayPath(gateway.name).segments match {
        case deployment :: _ :: Nil               ⇒ !deployments.exists(_.name == deployment)
        case deployment :: cluster :: port :: Nil ⇒ deployments.find(_.name == deployment).flatMap(deployment ⇒ deployment.clusters.find(_.name == cluster)).isEmpty
        case _                                    ⇒ false
      }
    }

    remove.foreach { gateway ⇒
      log.info(s"gateway removed: ${gateway.name}")
      IoC.actorFor[PersistenceActor] ! Delete(gateway.name, gateway.getClass)
    }

    keep
  }

  private def flush(deployments: List[Deployment]): List[Gateway] ⇒ Unit = { gateways ⇒
    val processed = gateways.map { gateway ⇒

      def targets(path: GatewayPath): List[Option[DeployedRouteTarget]] = path.segments match {

        case reference :: Nil ⇒
          gateways.find {
            _.name == reference
          }.flatMap { gw ⇒
            Option {
              DeployedRouteTarget(reference, gw.port.number)
            }
          } :: Nil

        case deployment :: _ :: Nil ⇒
          deployments.find {
            _.name == deployment
          }.flatMap {
            _.gateways.find(_.name == path.normalized)
          }.flatMap { gateway ⇒
            Option {
              DeployedRouteTarget(path.normalized, gateway.port.number)
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
            Option {
              DeployedRouteTarget(path.normalized, port)
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
                Option {
                  DeployedRouteTarget(instance.name, instance.host, instance.ports.get(port).get)
                }
            }
          }.getOrElse(Nil)

        case _ ⇒ None :: Nil
      }

      val routes: List[Option[DeployedRoute]] = gateway.routes.map {
        case route: AbstractRoute if route.length > 0 && route.length < 5 ⇒
          val optionalTargets = targets(route.path)
          if (optionalTargets.exists(_.isEmpty)) None else Option(DeployedRoute(route, optionalTargets.flatten))
        case route ⇒ throwException(UnsupportedRoutePathError(route.path))
      }

      if (routes.exists(_.isEmpty)) gateway.copy(active = false) else gateway.copy(routes = routes.flatten, active = true)
    }

    IoC.actorFor[GatewayDriverActor] ! Commit(processed.filter(_.active))

    processed.foreach { gateway ⇒ IoC.actorFor[PersistenceActor] ! Update(gateway) }
  }
}
