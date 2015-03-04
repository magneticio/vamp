package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{FlowGraph, ForeachSink, Source}
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import io.magnetic.vamp_core.operation.notification.{DeploymentSynchronizationFailure, OperationNotificationProvider}

import scala.util.{Failure, Success}

object DeploymentSynchronizationActor extends ActorDescription {

  def props: Props = Props(new DeploymentSynchronizationActor)

  case class Synchronize(deployment: Deployment)

  case class SynchronizeAll(deployments: List[Deployment])

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case Synchronize(deployment) => synchronize(deployment :: Nil)
    case SynchronizeAll(deployments) => synchronize(deployments)
  }

  private def synchronize(deployments: List[Deployment]): Unit = deployments.foreach(synchronize)

  private def synchronize(deployment: Deployment): Unit = {

    implicit val materializer = ActorFlowMaterializer()

    val sink = ForeachSink[DeploymentService] { service =>
      println(service.breed.name)
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
