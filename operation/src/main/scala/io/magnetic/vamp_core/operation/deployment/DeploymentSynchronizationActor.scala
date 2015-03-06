package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{FlowGraph, ForeachSink, Source}
import akka.util.Timeout
import io.magnetic.vamp_core.container_driver.ContainerDriverActor.Deploy
import io.magnetic.vamp_core.container_driver.{ContainerDriverActor, ContainerService}
import io.magnetic.vamp_core.model.artifact.DeploymentService.ReadyForDeployment
import io.magnetic.vamp_core.operation.notification.{DeploymentSynchronizationFailure, OperationNotificationProvider}

import scala.util.{Failure, Success}

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  case class Synchronize(deployment: Deployment)

  case class SynchronizeAll(deployments: List[Deployment])

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case Synchronize(deployment) => synchronize(deployment :: Nil)
    case SynchronizeAll(deployments) => synchronize(deployments)
  }

  private def synchronize(deployments: List[Deployment]): Unit = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout
    val containerServices = offLoad(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]
    deployments.foreach(synchronize(_, containerServices))
  }

  private def synchronize(deployment: Deployment, containerServices: List[ContainerService]): Unit = {
    implicit val materializer = ActorFlowMaterializer()

    val sink = ForeachSink[DeploymentService] { service =>
      service.state match {
        case ReadyForDeployment(initiated, _) => actorFor(ContainerDriverActor) ! Deploy(deployment, service)
      }

      println(s"${service.breed.name}: ${service.state.getClass.getSimpleName}")
    }

    val materialized = FlowGraph { implicit builder =>
      import akka.stream.scaladsl.FlowGraphImplicits._
      Source(() => deployment.clusters.iterator).mapConcat({ cluster =>
        cluster.services
      }) ~> sink
    }.run()

    materialized.get(sink).onComplete {
      case Success(_) => log.debug(s"Synchronization is done for deployment: ${deployment.name}")
      case Failure(e) => exception(DeploymentSynchronizationFailure(deployment, e))
    }
  }
}
