package io.vamp.operation.gateway

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.PersistenceActor.{ Update, Create, Delete }
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
    case SynchronizeAll ⇒ synchronize()
    case any            ⇒ if (sender() != IoC.actorFor[PersistenceActor]) unsupported(UnsupportedGatewayRequest(any))
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    (for {
      gateways ← allArtifacts[Gateway]
      deployments ← allArtifacts[Deployment] map { deployments ⇒ update(gateways, deployments) }
    } yield (gateways, deployments)) onComplete {
      case Success((gateways, deployments)) ⇒ (add(deployments) andThen remove(deployments) andThen flush)(gateways)
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

    val newly = deployments.flatMap { deployment ⇒
      deployment.gateways.map(gateway ⇒ gateway.copy(name = GatewayPath(deployment.name, gateway.port.number).source)) ++
        deployment.clusters.filter(_.services.forall(_.state.isDone)).flatMap { cluster ⇒
          cluster.routing.map { routing ⇒
            val port = routing.port.copy(value = cluster.portMapping.get(routing.port.name).flatMap { number ⇒ Port(number, routing.port.`type`).value })
            routing.copy(name = GatewayPath(deployment.name, cluster.name, routing.port.name).source, port = port)
          }
        }
    } filterNot { gateway ⇒
      gateways.exists(_.name == gateway.name)
    }

    newly.foreach { gateway ⇒
      log.info(s"gateway created: ${gateway.name}")
      IoC.actorFor[PersistenceActor] ! Create(gateway)
    }

    newly ++ gateways
  }

  private def remove(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒

    val (remove, keep) = gateways.partition { gateway ⇒
      val name = GatewayPath(gateway.name)
      name.path.size > 1 && !deployments.exists(_.name == name.path.head)
    }

    remove.foreach { gateway ⇒
      log.info(s"gateway removed: ${gateway.name}")
      IoC.actorFor[PersistenceActor] ! Delete(gateway.name, gateway.getClass)
    }

    keep
  }

  private def flush: List[Gateway] ⇒ Unit = { gateways ⇒
    // TODO set active, persist, send to driver
  }
}
