package io.vamp.core.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.DefaultPackageMessageResolverProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.{DeEscalate, Escalate}
import io.vamp.core.operation.notification._
import io.vamp.core.operation.sla.SlaActor.SlaProcessAll
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.notification.PulseNotificationProvider

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

class SlaActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with PulseNotificationProvider with DefaultPackageMessageResolverProvider {

  def tags = "sla" :: Nil

  def receive: Receive = {
    case SlaProcessAll =>
      implicit val timeout = PersistenceActor.timeout
      offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => check(deployments.asInstanceOf[List[Deployment]])
        case any => exception(InternalServerError(any))
      }
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
    val from = OffsetDateTime.now().minus((sla.interval + sla.cooldown).toSeconds, ChronoUnit.SECONDS)

    if (offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.EventExists(deployment, cluster, from)).asInstanceOf[Boolean]) {
      val to = OffsetDateTime.now()
      val from = to.minus(sla.interval.toSeconds, ChronoUnit.SECONDS)

      val responseTimes = cluster.routes.keys.map(value => TcpPort("", None, Some(value), Trait.Direction.Out)).map { port =>
        offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.ResponseTime(deployment, cluster, port, from, to)).asInstanceOf[Long]
      }

      if (responseTimes.max > sla.upper.toMillis)
        info(Escalate(deployment, cluster))
      else if (responseTimes.max < sla.lower.toMillis)
        info(DeEscalate(deployment, cluster))
    }
  }
}
