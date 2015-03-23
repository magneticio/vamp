package io.vamp.core.operation.sla

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.core.model.artifact.DeploymentService.ReadyForDeployment
import io.vamp.core.model.artifact._
import io.vamp.core.operation.notification.{InternalServerError, OperationNotificationProvider, UnsupportedEscalationType}
import io.vamp.core.operation.sla.EscalationActor.EscalationProcessAll
import io.vamp.core.persistence.actor.PersistenceActor

import scala.language.postfixOps

object EscalationSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaSchedulerActor]

}

class EscalationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = actorFor(SlaActor) ! EscalationProcessAll

}

object EscalationActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaActor]

  object EscalationProcessAll

}

class SlaMonitorActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case EscalationProcessAll =>
      implicit val timeout = PersistenceActor.timeout
      offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
        case any => exception(InternalServerError(any))
      }
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
          case ScaleInstancesEscalation(_, minimum, maximum, scaleBy, _) =>
            val instances = if (escalate) scale.instances + scaleBy else scale.instances - scaleBy
            if (instances <= maximum && instances >= minimum) commit(targetCluster, scale.copy(instances = instances.toInt))

          case ScaleCpuEscalation(_, minimum, maximum, scaleBy, _) =>
            val cpu = if (escalate) scale.cpu + scaleBy else scale.cpu - scaleBy
            if (cpu <= maximum && cpu >= minimum) commit(targetCluster, scale.copy(cpu = cpu))

          case ScaleMemoryEscalation(_, minimum, maximum, scaleBy, _) =>
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

