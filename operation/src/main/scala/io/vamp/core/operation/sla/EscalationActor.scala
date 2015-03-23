package io.vamp.core.operation.sla

import java.time.OffsetDateTime

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.core.model.artifact.DeploymentService.ReadyForDeployment
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.{DeEscalate, Escalate}
import io.vamp.core.operation.notification.{InternalServerError, OperationNotificationProvider, UnsupportedEscalationType}
import io.vamp.core.operation.sla.EscalationActor.EscalationProcessAll
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

object EscalationSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaSchedulerActor]

}

class EscalationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  private var windowStart: Option[OffsetDateTime] = None

  def tick() = windowStart match {
    case Some(from) =>
      val to = OffsetDateTime.now().withNano(0)
      actorFor(SlaActor) ! EscalationProcessAll(from, to)
      windowStart = Some(to)

    case None =>
  }

  override def schedule(period: FiniteDuration) = {
    period.toNanos match {
      case interval if interval > 0 => if (windowStart.isEmpty) windowStart = Some(OffsetDateTime.now().withNano(0))
      case _ => windowStart = None
    }
    super.schedule(period)
  }

}

object EscalationActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaActor]

  case class EscalationProcessAll(from: OffsetDateTime, to: OffsetDateTime)

}

class SlaMonitorActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case EscalationProcessAll(from, to) =>
      implicit val timeout = PersistenceActor.timeout
      offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]], from, to)
        case any => exception(InternalServerError(any))
      }
  }

  private def check(deployments: List[Deployment], from: OffsetDateTime, to: OffsetDateTime) = {
    deployments.foreach(deployment => {
      deployment.clusters.foreach(cluster => cluster.sla match {
        case None =>
        case Some(sla) =>
          sla.escalations.foreach { _ =>
            implicit val timeout = PulseDriverActor.timeout
            offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.QuerySlaNotificationEvents(deployment, cluster, from, to)) match {
              case escalationEvents: List[_] => escalationEvents.foreach {
                case Escalate(_, _, _) => escalate(deployment, cluster, sla, escalate = true)
                case DeEscalate(_, _, _) => escalate(deployment, cluster, sla, escalate = false)
                case _ =>
              }
              case any => exception(InternalServerError(any))
            }
          }
      })
    })
  }

  private def escalate(deployment: Deployment, cluster: DeploymentCluster, sla: Sla, escalate: Boolean) = {
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
        // Scale only the first service.
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

