package io.magnetic.vamp_core.operation.sla

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_core.model.artifact.DeploymentService.ReadyForDeployment
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.operation.notification.{InternalServerError, OperationNotificationProvider, UnsupportedEscalationType}
import io.magnetic.vamp_core.operation.sla.EscalationActor.Period
import io.magnetic.vamp_core.persistence.actor.PersistenceActor
import io.vamp.common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}

import scala.concurrent.duration._
import scala.language.postfixOps

object EscalationActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaActor]

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
            offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
              case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
              case any => exception(InternalServerError(any))
            }
          }
        }))
      } else timer = None
  }

  private def check(deployments: List[Deployment]) = {
    // TODO for each cluster get escalations & escalate/deescalate Pulse events
    //    deployments.foreach(deployment => {
    //      deployment.clusters.foreach(cluster =>
    //        cluster.sla match {
    //          case Some(sla: ResponseTimeSlidingWindowSla) => responseTimeSlidingWindow(deployment, cluster, sla)
    //          case Some(s: GenericSla) => exception(UnsupportedSlaType(s.`type`))
    //          case Some(s: Sla) => error(UnsupportedSlaType(s.name))
    //          case None =>
    //        })
    //    })
  }

  private def escalation(deployment: Deployment, cluster: DeploymentCluster, sla: Sla, escalate: Boolean) = {
    sla.escalations.foreach {
      case e: ScaleEscalation[_] => scaleEscalation(deployment, cluster, e, escalate)
      case e: GenericEscalation => info(UnsupportedEscalationType(e.`type`))
      case e: Escalation => error(UnsupportedEscalationType(e.name))
    }
  }

  private def scaleEscalation(deployment: Deployment, cluster: DeploymentCluster, escalation: ScaleEscalation[_], escalate: Boolean) = {
    (escalation.targetCluster match {
      case None => Some(cluster)
      case Some(name) => deployment.clusters.find(_.name == name) match {
        case None => None
        case Some(c) => Some(c)
      }
    }) match {
      case None =>
      case Some(targetCluster) =>
        val scale = cluster.services.head.scale.get
        escalation match {
          case ScaleInstancesEscalation(_, minimum, maximum, scaleBy) =>
            val instances = if (escalate) scale.instances + scaleBy else scale.instances - scaleBy
            if (instances <= maximum && instances >= minimum) commit(targetCluster, scale.copy(instances = instances.toInt))

          case ScaleCpuEscalation(_, minimum, maximum, scaleBy) =>
            val cpu = if (escalate) scale.cpu + scaleBy else scale.cpu - scaleBy
            if (cpu <= maximum && cpu >= minimum) commit(targetCluster, scale.copy(cpu = cpu))

          case ScaleMemoryEscalation(_, minimum, maximum, scaleBy) =>
            val memory = if (escalate) scale.memory + scaleBy else scale.memory - scaleBy
            if (memory <= maximum && memory >= minimum) commit(targetCluster, scale.copy(memory = memory))
        }
    }

    def commit(targetCluster: DeploymentCluster, scale: DefaultScale) = {
      // Scale only the first service.
      actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(c => {
        if (c.name == targetCluster.name)
          c.copy(services = c.services match {
            case head :: tail => head.copy(scale = Some(scale), state = ReadyForDeployment()) :: tail
            case Nil => Nil
          })
        else
          c
      })))
    }
  }
}

