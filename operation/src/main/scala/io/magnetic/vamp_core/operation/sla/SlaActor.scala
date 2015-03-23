package io.magnetic.vamp_core.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.operation.notification._
import io.magnetic.vamp_core.operation.sla.SlaActor.Period
import io.magnetic.vamp_core.pulse_driver.PulseDriverActor
import io.vamp.common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.vamp.core.persistence.actor.PersistenceActor

import scala.concurrent.duration._
import scala.language.postfixOps

object SlaActor extends ActorDescription {

  def props(args: Any*): Props = Props[SlaActor]

  case class Period(period: Int)

}

class SlaActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with SlaNotificationProvider {

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
    deployments.foreach(deployment => {
      deployment.clusters.foreach(cluster =>
        cluster.sla match {
          case Some(sla: ResponseTimeSlidingWindowSla) => responseTimeSlidingWindow(deployment, cluster, sla)
          case Some(s: EscalationOnlySla) =>
          case Some(s: GenericSla) => info(UnsupportedSlaType(s.`type`))
          case Some(s: Sla) => error(UnsupportedSlaType(s.name))
          case None =>
        })
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: ResponseTimeSlidingWindowSla) = {
    log.debug(s"response time sliding window sla check for: ${deployment.name}/${cluster.name}")

    implicit val timeout = PulseDriverActor.timeout
    val timestamp = offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.LastSlaEventTimestamp(deployment, cluster)).asInstanceOf[OffsetDateTime]
    if (timestamp.isBefore(OffsetDateTime.now().minus((sla.interval + sla.cooldown).toSeconds, ChronoUnit.SECONDS))) {
      val responseTime = offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.ResponseTime(deployment, cluster, sla.interval.toSeconds)).asInstanceOf[Long]
      if (responseTime > sla.upper.toMillis)
        info(Escalate(deployment, cluster, sla))
      else if (responseTime < sla.lower.toMillis)
        info(DeEscalate(deployment, cluster, sla))
    }
  }
}
