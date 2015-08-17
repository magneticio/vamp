package io.vamp.core.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact._
import io.vamp.core.model.event.{Aggregator, Event, EventQuery, TimeRange, _}
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.operation.notification._
import io.vamp.core.operation.sla.SlaActor.SlaProcessAll
import io.vamp.core.persistence.{PaginationSupport, PersistenceActor}
import io.vamp.core.pulse.PulseActor
import io.vamp.core.pulse.PulseActor.Publish
import io.vamp.core.router_driver.DefaultRouterDriverNameMatcher

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

class SlaActor extends SlaPulse with PaginationSupport with CommonSupportForActors with OperationNotificationProvider {

  def receive: Receive = {
    case SlaProcessAll =>
      implicit val timeout = PersistenceActor.timeout
      allArtifacts(classOf[Deployment]) match {
        case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
        case any => reportException(InternalServerError(any))
      }
  }

  override def info(notification: Notification): Unit = {
    notification match {
      case se: SlaEvent => actorFor(PulseActor) ! Publish(Event(Set("sla") ++ se.tags, se.value, se.timestamp))
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
            case Some(s: Sla) => throwException(UnsupportedSlaType(s.name))
            case None =>
          })
      } catch {
        case any: Throwable => reportException(InternalServerError(any))
      }
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: ResponseTimeSlidingWindowSla) = {
    log.debug(s"response time sliding window sla check for: ${deployment.name}/${cluster.name}")

    val from = OffsetDateTime.now().minus((sla.interval + sla.cooldown).toSeconds, ChronoUnit.SECONDS)

    if (!eventExists(deployment, cluster, from)) {
      val to = OffsetDateTime.now()
      val from = to.minus(sla.interval.toSeconds, ChronoUnit.SECONDS)

      val responseTimes = cluster.routes.keys.map(value => Port.portFor(value)).flatMap({ port =>
        responseTime(deployment, cluster, port, from, to) match {
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
  }
}

trait SlaPulse extends DefaultRouterDriverNameMatcher {
  this: FutureSupport with ActorSupport with ActorLogging =>

  implicit val timeout = PulseActor.timeout

  def eventExists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Boolean = {
    val eventQuery = EventQuery(SlaEvent.slaTags(deployment, cluster), Some(TimeRange(Some(from), Some(OffsetDateTime.now()), includeLower = true, includeUpper = true)), Some(Aggregator(Aggregator.count)))
    offload(actorFor(PulseActor) ? PulseActor.QueryFirst(eventQuery)) match {
      case LongValueAggregationResult(count) => count > 0
      case other =>
        log.error(other.toString)
        true
    }
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime): Option[Double] = {
    val eventQuery = EventQuery(Set(s"routes:${clusterRouteName(deployment, cluster, port)}", "metrics:rtime"), Some(TimeRange(Some(from), Some(to), includeLower = true, includeUpper = true)), Some(Aggregator(Aggregator.average)))
    offload(actorFor(PulseActor) ? PulseActor.QueryFirst(eventQuery)) match {
      case DoubleValueAggregationResult(value) => Some(value)
      case other =>
        log.error(other.toString)
        None
    }
  }
}
