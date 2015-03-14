package io.magnetic.vamp_core.operation.sla

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.magnetic.vamp_core.model.artifact.DeploymentService.ReadyForDeployment
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.operation.notification.{InternalServerError, OperationNotificationProvider, UnsupportedEscalationType, UnsupportedSlaType}
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor.Period
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.persistence.PersistenceActor.All
import io.magnetic.vamp_core.pulse_driver.PulseDriverActor

import scala.collection._
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
          case Some(sla: DefaultSla) if sla.`type` == "response_time_sliding_window" => try {
            responseTimeSlidingWindow(deployment, cluster, sla)
          } catch {
            case e: Exception => exception(InternalServerError(e))
          }
          case Some(s: DefaultSla) => exception(UnsupportedSlaType(s.`type`))
          case Some(s: Sla) => error(UnsupportedSlaType(s.name))
        })
    })
  }

  private def responseTimeSlidingWindow(deployment: Deployment, cluster: DeploymentCluster, sla: DefaultSla) = {
    log.debug(s"sla check for: ${deployment.name}/${cluster.name}")

    val threshold = sla.parameters.get("threshold")
    val window = threshold.flatMap(threshold => threshold.asInstanceOf[mutable.Map[String, Any]].get("window"))

    for {
      upper <- threshold.flatMap(threshold => threshold.asInstanceOf[mutable.Map[String, Any]].get("upper")).flatMap(value => Some(value.asInstanceOf[Int]))
      lower <- threshold.flatMap(threshold => threshold.asInstanceOf[mutable.Map[String, Any]].get("lower")).flatMap(value => Some(value.asInstanceOf[Int]))
      interval <- window.flatMap(window => window.asInstanceOf[mutable.Map[String, Any]].get("interval")).flatMap(value => Some(value.asInstanceOf[Int]))
      cooldown <- window.flatMap(window => window.asInstanceOf[mutable.Map[String, Any]].get("cooldown")).flatMap(value => Some(value.asInstanceOf[Int]))
    } yield {
      implicit val timeout = PulseDriverActor.timeout
      val timestamp = offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.LastSlaEventTimestamp(deployment, cluster)).asInstanceOf[OffsetDateTime]
      if (timestamp.isBefore(OffsetDateTime.now().minus(interval + cooldown, ChronoUnit.SECONDS))) {
        val responseTime = offLoad(actorFor(PulseDriverActor) ? PulseDriverActor.ResponseTime(deployment, cluster, interval)).asInstanceOf[Int]
        if (responseTime > upper)
          escalation(deployment, cluster, sla, escalate = true)
        else if (responseTime < lower)
          escalation(deployment, cluster, sla, escalate = false)
      }
    }
  }

  private def escalation(deployment: Deployment, cluster: DeploymentCluster, sla: DefaultSla, escalate: Boolean) = {
    sla.escalations.foreach({
      case DefaultEscalation(n, t, p) if t == "scale_instances" || t == "scale_cpu" || t == "scale_memory" =>
        for {
          minimum <- p.get("minimum").flatMap(value => Some(value.asInstanceOf[Double]))
          maximum <- p.get("maximum").flatMap(value => Some(value.asInstanceOf[Double]))
          scaleBy <- p.get("scale_by").flatMap(value => Some(value.asInstanceOf[Int]))
        } yield {
          val scale = cluster.services(0).scale.get
          t match {
            case "scale_instances" =>
              val instances = if (escalate) scale.instances + scaleBy else scale.instances - scaleBy
              if (instances <= maximum && instances >= minimum) {
                persist(Some(scale.copy(instances = instances.toInt)))
              }
            case "scale_cpu" =>
              val cpu = if (escalate) scale.cpu + scaleBy else scale.cpu - scaleBy
              if (cpu <= maximum && cpu >= minimum) {
                persist(Some(scale.copy(cpu = cpu)))
              }
            case "scale_memory" =>
              val memory = if (escalate) scale.memory + scaleBy else scale.memory - scaleBy
              if (memory <= maximum && memory >= minimum) {
                persist(Some(scale.copy(memory = memory)))
              }
          }
        }
      case e: DefaultEscalation => exception(UnsupportedEscalationType(e.`type`))
      case e: Escalation => error(UnsupportedEscalationType(e.name))
    })

    def persist(scale: Option[DefaultScale]) = {
      actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(c => {
        if (c.name == cluster.name)
          c.copy(services = c.services match {
            case head :: tail => head.copy(scale = scale, state = ReadyForDeployment()) :: tail
          })
        else
          c
      })))
    }
  }
}
