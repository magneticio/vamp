package io.vamp.operation.gateway

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.model.artifact.{ Deployment, Gateway, GatewayPath }
import io.vamp.operation.gateway.GatewaySynchronizationActor.SynchronizeAll
import io.vamp.operation.notification._
import io.vamp.persistence.PersistenceActor.{ Create, Delete }
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

  /*
  private var currentPort = portRangeLower - 1


  case portAssignment(deployment, port) ⇒
      if (currentPort == portRange(1))
        reportException(NoAvailablePortError(portRange(0), portRange(1)))
      else {
        currentPort += 1
        currentPort
      }
   */
  def receive = {
    case SynchronizeAll ⇒ synchronize()
    case _: Gateway     ⇒ // ignore PersistenceActor replies
    case any            ⇒ unsupported(UnsupportedGatewayRequest(any))
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    (for {
      gateways ← allArtifacts[Gateway]
      deployments ← allArtifacts[Deployment]
    } yield (gateways, deployments)) onComplete {
      case Success((gateways, deployments)) ⇒ (add(deployments) andThen remove(deployments) andThen flush)(gateways)
      case Failure(error)                   ⇒ throwException(InternalServerError(error))
    }
  }

  private def add(deployments: List[Deployment]): List[Gateway] ⇒ List[Gateway] = { gateways ⇒

    val newly = deployments.flatMap { deployment ⇒
      deployment.gateways.map(gateway ⇒ gateway.copy(name = GatewayPath(deployment.name, gateway.port.number).source)) ++
        deployment.clusters.filter(_.services.forall(_.state.isDone)).flatMap { cluster ⇒
          cluster.routing.map(routing ⇒ routing.copy(name = GatewayPath(deployment.name, cluster.name, routing.port.name).source))
        }
    } filterNot { gateway ⇒
      gateways.exists(_.name == gateway.name)
    } map { gateway ⇒
      if (gateway.port.value.isDefined) gateway else gateway.copy(port = gateway.port.copy(value = Some("0")))
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
  }
}
