package io.vamp.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.model.artifact._
import io.vamp.model.event.{ Aggregator, Event, EventQuery, TimeRange, _ }
import io.vamp.model.notification.{ DeEscalate, Escalate, SlaEvent }
import io.vamp.operation.notification._
import io.vamp.operation.sla.SlaActor.SlaProcessAll
import io.vamp.persistence.{ ArtifactPaginationSupport, EventPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future
import scala.language.postfixOps

class SlaSchedulerActor extends SchedulerActor with OperationNotificationProvider {
  def tick() = IoC.actorFor[SlaActor] ! SlaProcessAll
}

object SlaActor {

  object SlaProcessAll

}

class SlaActor extends SlaPulse with ArtifactPaginationSupport with EventPaginationSupport with CommonSupportForActors with OperationNotificationProvider {

  def receive: Receive = {
    case SlaProcessAll ⇒
      implicit val timeout = PersistenceActor.timeout
      allArtifacts[Deployment] map check
  }

  override def info(notification: Notification): Unit = {
    notification match {
      case se: SlaEvent ⇒ actorFor[PulseActor] ! Publish(Event(Set("sla") ++ se.tags, se.value, se.timestamp))
      case _            ⇒
    }
    super.info(notification)
  }

  private def check(deployments: List[Deployment]) = {
    deployments.foreach(deployment ⇒ {
      try {
        deployment.clusters.foreach(cluster ⇒
          cluster.sla match {
            case Some(sla: ResponseTimeSlidingWindowSla) ⇒ responseTimeSlidingWindow(deployment, cluster, sla)
            case Some(s: EscalationOnlySla)              ⇒
            case Some(s: GenericSla)                     ⇒ info(UnsupportedSlaType(s.`type`))
            case Some(s: Sla)                            ⇒ throwException(UnsupportedSlaType(s.name))
            case None                                    ⇒
          })
      } catch {
        case any: Throwable ⇒ reportException(InternalServerError(any))
      }
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: ResponseTimeSlidingWindowSla) = {
    log.debug(s"response time sliding window sla check for: ${deployment.name}/${cluster.name}")

    if (cluster.services.forall(_.state.isDone)) {
      val from = OffsetDateTime.now().minus((sla.interval + sla.cooldown).toSeconds, ChronoUnit.SECONDS)

      eventExists(deployment, cluster, from) map {
        case true ⇒ log.debug(s"escalation event found within cooldown + interval period for: ${deployment.name}/${cluster.name}.")
        case false ⇒
          log.debug(s"escalation event not found within cooldown + interval period for: ${deployment.name}/${cluster.name}.")

          val to = OffsetDateTime.now()
          val from = to.minus(sla.interval.toSeconds, ChronoUnit.SECONDS)

          Future.sequence(cluster.portMapping.keys.map({ portName ⇒
            responseTime(deployment, cluster, portName, from, to)
          })) map {
            case optionalResponseTimes ⇒
              val responseTimes = optionalResponseTimes.flatten
              if (responseTimes.nonEmpty) {
                val maxResponseTimes = responseTimes.max
                log.debug(s"escalation max response time for ${deployment.name}/${cluster.name}: $maxResponseTimes.")
                if (maxResponseTimes > sla.upper.toMillis)
                  info(Escalate(deployment, cluster))
                else if (maxResponseTimes < sla.lower.toMillis)
                  info(DeEscalate(deployment, cluster))
              }
          }
      }
    }
  }
}

trait SlaPulse {
  this: CommonSupportForActors ⇒

  implicit val timeout = PulseActor.timeout

  def eventExists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Future[Boolean] = {
    eventCount(SlaEvent.slaTags(deployment, cluster), from, OffsetDateTime.now(), 1) map {
      case count ⇒ count > 0
    }
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, portName: String, from: OffsetDateTime, to: OffsetDateTime): Future[Option[Double]] = {
    val tags = Set(s"gateways:${deployment.name}_${cluster.name}_$portName", "metrics:responseTime")

    eventCount(tags, from, to, -1) flatMap {
      case count if count >= 0 ⇒
        val eventQuery = EventQuery(tags, Some(TimeRange(Some(from), Some(to), includeLower = true, includeUpper = true)), Some(Aggregator(Aggregator.average)))
        actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
          case DoubleValueAggregationResult(value) ⇒ Some(value)
          case other                               ⇒ log.error(other.toString); None
        }
      case _ ⇒ Future.successful(None)
    }
  }

  def eventCount(tags: Set[String], from: OffsetDateTime, to: OffsetDateTime, onError: Long): Future[Long] = {
    val eventQuery = EventQuery(tags, Some(TimeRange(Some(from), Some(OffsetDateTime.now()), includeLower = true, includeUpper = true)), Some(Aggregator(Aggregator.count)))
    actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
      case LongValueAggregationResult(count) ⇒ count
      case other                             ⇒ onError
    }
  }
}
