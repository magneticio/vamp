package io.vamp.core.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.operation.notification._
import io.vamp.core.operation.sla.SlaActor.SlaProcessAll
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse.PulseDriverActor
import io.vamp.core.pulse.PulseDriverActor.Publish
import io.vamp.core.pulse.event.Event

import scala.language.postfixOps

object SlaSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaSchedulerActor]

}

class SlaSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = actorFor(SlaActor) ! SlaProcessAll

}

object SlaActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaActor]

  object SlaProcessAll

}

class SlaActor extends CommonSupportForActors with OperationNotificationProvider {

  def receive: Receive = {
    case SlaProcessAll =>
      implicit val timeout = PersistenceActor.timeout
      offload(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
        case any => exception(InternalServerError(any))
      }
  }

  override def info(notification: Notification): Unit = {
    notification match {
      case se: SlaEvent => actorFor(PulseDriverActor) ! Publish(Event(Set("sla") ++ se.tags, se.value, se.timestamp))
      case _ =>
    }
    super.info(notification)
  }

  private def check(deployments: List[Deployment]) = {
    deployments.foreach(deployment => {
      try {
        deployment.clusters.foreach(cluster =>
          cluster.sla match {
            case Some(sla: ResponseTimeSlidingWindowSla) => responseTimeSlidingWindow(deployment, cluster, sla)
            case Some(s: EscalationOnlySla) =>
            case Some(s: GenericSla) => info(UnsupportedSlaType(s.`type`))
            case Some(s: Sla) => error(UnsupportedSlaType(s.name))
            case None =>
          })
      } catch {
        case any: Throwable => exception(InternalServerError(any))
      }
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: ResponseTimeSlidingWindowSla) = {
    log.debug(s"response time sliding window sla check for: ${deployment.name}/${cluster.name}")

    implicit val timeout = PulseDriverActor.timeout
    val from = OffsetDateTime.now().minus((sla.interval + sla.cooldown).toSeconds, ChronoUnit.SECONDS)

    offload(actorFor(PulseDriverActor) ? PulseDriverActor.EventExists(deployment, cluster, from)) match {
      case exists: Boolean => if (!exists) {
        val to = OffsetDateTime.now()
        val from = to.minus(sla.interval.toSeconds, ChronoUnit.SECONDS)

        val responseTimes = cluster.routes.keys.map(value => Port.portFor(value)).flatMap({ port =>
          offload(actorFor(PulseDriverActor) ? PulseDriverActor.ResponseTime(deployment, cluster, port, from, to)) match {
            case Some(responseTime: Double) => responseTime :: Nil
            case _ => Nil
          }
        })

        if (responseTimes.nonEmpty) {
          val maxResponseTimes = responseTimes.max

          if (maxResponseTimes > sla.upper.toMillis)
            info(Escalate(deployment, cluster))
          else if (maxResponseTimes < sla.lower.toMillis)
            info(DeEscalate(deployment, cluster))
        }
      } else log.debug(s"escalation event found within cooldown + interval period for: ${deployment.name}/${cluster.name}")
      case _ =>
    }
  }
}
