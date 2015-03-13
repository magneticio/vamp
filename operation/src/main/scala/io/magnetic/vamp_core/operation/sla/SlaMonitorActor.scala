package io.magnetic.vamp_core.operation.sla

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.magnetic.vamp_core.model.artifact.{DefaultSla, Deployment, DeploymentCluster}
import io.magnetic.vamp_core.operation.notification.{InternalServerError, OperationNotificationProvider}
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor.Period
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.persistence.PersistenceActor.All

import scala.concurrent.duration._
import scala.language.postfixOps

object SlaMonitorActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaMonitorActor]

  case class Period(period: Int)

}

class SlaMonitorActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private var timer: Option[Cancellable] = None

  def receive: Receive = {
    case Period(period) =>
      timer.map(_.cancel())
      if (period > 0) {
        implicit val actorSystem = context.system
        implicit val timeout = PersistenceActor.timeout
        timer = Some(context.system.scheduler.schedule(0 milliseconds, period seconds, new Runnable {
          def run() = {
            offLoad(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
              case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
              case any => exception(InternalServerError(any))
            }
          }
        }))
      } else timer = None
  }

  private def check(deployments: List[Deployment]) = {
    deployments.foreach(deployment => {
      deployment.clusters.foreach(cluster =>
        cluster.sla match {
          case Some(sla: DefaultSla) if sla.`type` == "response_time_sliding_window" => responseTimeSlidingWindow(deployment, cluster, sla)
          case _ =>
        })
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: DefaultSla) = {
    log.info(s"sla check for: ${deployment.name}/${cluster.name}")
  }
}