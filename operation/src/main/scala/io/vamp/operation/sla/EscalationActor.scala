package io.vamp.operation.sla

import java.time.OffsetDateTime

import akka.actor._
import akka.util.Timeout
import io.vamp.common.RootAnyMap
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact.DeploymentService.Status.Phase.Initiated
import io.vamp.model.artifact._
import io.vamp.model.event.{ Event, EventQuery, TimeRange }
import io.vamp.model.notification.{ DeEscalate, Escalate, SlaEvent }
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.operation.notification.{ InternalServerError, OperationNotificationProvider, UnsupportedEscalationType }
import io.vamp.operation.sla.EscalationActor.EscalationProcessAll
import io.vamp.persistence.EventPaginationSupport
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.pulse.PulseActor
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class EscalationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  private var windowStart: Option[OffsetDateTime] = None

  def tick() = windowStart match {
    case Some(from) ⇒
      val to = OffsetDateTime.now().withNano(0)
      actorFor[EscalationActor] ! EscalationProcessAll(from, to)
      windowStart = Some(to)

    case None ⇒
  }

  override def schedule(period: FiniteDuration, initialDelay: FiniteDuration) = {
    period.toNanos match {
      case interval if interval > 0 ⇒ if (windowStart.isEmpty) windowStart = Some(OffsetDateTime.now().withNano(0))
      case _                        ⇒ windowStart = None
    }
    super.schedule(period, initialDelay)
  }

}

object EscalationActor {

  case class EscalationProcessAll(from: OffsetDateTime, to: OffsetDateTime)

}

class EscalationActor extends EventPaginationSupport with CommonSupportForActors with OperationNotificationProvider with VampJsonFormats {

  def tags = Set("escalation")

  implicit val timeout: Timeout = Timeout(5.second)
  def receive: Receive = {
    case EscalationProcessAll(from, to) ⇒
      VampPersistence().getAll[Deployment]().map(_.response).map(check(from, to)(_))
  }

  private def check(from: OffsetDateTime, to: OffsetDateTime)(deployments: List[Deployment]): Unit = {

    def escalation(deployment: Deployment, cluster: DeploymentCluster, sla: Sla, escalate: Boolean) = {
      escalateToOne(deployment, cluster, ToOneEscalation("", RootAnyMap.empty, sla.escalations), escalate) match {
        case Some(d) ⇒ VampPersistence().update[Deployment](deploymentSerilizationSpecifier.idExtractor(d), _ ⇒ d)
        case _       ⇒
      }
    }

    deployments.foreach(deployment ⇒ {
      try {
        deployment.clusters.foreach(cluster ⇒ cluster.sla match {
          case None ⇒
          case Some(sla) ⇒
            forEach(querySlaEvents(deployment, cluster, from, to), {
              case Escalate(d, c, _) if d.name == deployment.name && c.name == cluster.name ⇒ escalation(deployment, cluster, sla, escalate = true)
              case DeEscalate(d, c, _) if d.name == deployment.name && c.name == cluster.name ⇒ escalation(deployment, cluster, sla, escalate = false)
              case _ ⇒
            }: (SlaEvent) ⇒ Unit)
        })
      }
      catch {
        case any: Throwable ⇒ reportException(InternalServerError(any))
      }
    })
  }

  private def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[Stream[Future[List[SlaEvent]]]] = {
    implicit val timeout = PulseActor.timeout()
    val eventQuery = EventQuery(SlaEvent.slaTags(deployment, cluster), None, Some(TimeRange(Some(from), Some(to), includeLower = false, includeUpper = true)))

    collectEach[Event, SlaEvent](allEvents(eventQuery), {
      case event if Escalate.tags.forall(event.tags.contains)   ⇒ Escalate(deployment, cluster, event.timestamp)
      case event if DeEscalate.tags.forall(event.tags.contains) ⇒ DeEscalate(deployment, cluster, event.timestamp)
    })
  }

  private def escalateToAll(deployment: Deployment, cluster: DeploymentCluster, escalations: List[Escalation], escalate: Boolean): Option[Deployment] = {
    log.debug(s"to all escalation: ${deployment.name}/${cluster.name}")
    escalations.foldLeft[Option[Deployment]](None)((op1, op2) ⇒ op1 match {
      case Some(d) ⇒ escalateToOne(d, cluster, op2, escalate)
      case None    ⇒ escalateToOne(deployment, cluster, op2, escalate)
    })
  }

  private def escalateToOne(deployment: Deployment, cluster: DeploymentCluster, escalation: Escalation, escalate: Boolean): Option[Deployment] = {
    log.debug(s"to one escalation: ${deployment.name}/${cluster.name}")
    escalation match {

      case e: ToAllEscalation ⇒ escalateToAll(deployment, cluster, e.escalations, escalate)

      case e: ToOneEscalation ⇒ (if (escalate) e.escalations else e.escalations.reverse).foldLeft[Option[Deployment]](None)((op1, op2) ⇒ if (op1.isDefined) op1 else escalateToOne(deployment, cluster, op2, escalate))

      case e: ScaleEscalation ⇒ scaleEscalation(deployment, cluster, e, escalate)

      case e: GenericEscalation ⇒
        info(UnsupportedEscalationType(e.`type`))
        None

      case e: Escalation ⇒ throwException(UnsupportedEscalationType(e.name))
    }
  }

  private def scaleEscalation(deployment: Deployment, cluster: DeploymentCluster, escalation: ScaleEscalation, escalate: Boolean): Option[Deployment] = {
    log.debug(s"scale escalation: ${deployment.name}/${cluster.name}")

    def commit(targetCluster: DeploymentCluster, scale: DefaultScale): Option[Deployment] = {
      // Scale only the first service.
      Option(deployment.copy(clusters = deployment.clusters.map(c ⇒ {
        if (c.name == targetCluster.name)
          c.copy(services = c.services match {
            case head :: tail ⇒ head.copy(scale = Some(scale), status = head.status.copy(phase = Initiated())) :: tail
            case Nil          ⇒ Nil
          })
        else
          c
      })))
    }

    (escalation.targetCluster match {
      case None ⇒ deployment.clusters.find(_.name == cluster.name)
      case Some(name) ⇒ deployment.clusters.find(_.name == name) match {
        case None    ⇒ None
        case Some(c) ⇒ Some(c)
      }
    }) match {
      case None ⇒ None
      case Some(targetCluster) ⇒
        // Scale only the first service.
        val scale = targetCluster.services.head.scale.get
        escalation match {

          case ScaleInstancesEscalation(_, _, minimum, maximum, scaleBy, _) ⇒
            val instances = if (escalate) scale.instances + scaleBy else scale.instances - scaleBy
            if (instances <= maximum && instances >= minimum) {
              log.info(s"scale instances: ${deployment.name}/${targetCluster.name} to $instances")
              commit(targetCluster, scale.copy(instances = instances.toInt))
            }
            else {
              log.debug(s"scale instances not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.instances}")
              None
            }

          case ScaleCpuEscalation(_, _, minimum, maximum, scaleBy, _) ⇒
            val cpu = if (escalate) scale.cpu.value + scaleBy else scale.cpu.value - scaleBy
            if (cpu <= maximum && cpu >= minimum) {
              log.info(s"scale cpu: ${deployment.name}/${targetCluster.name} to $cpu")
              commit(targetCluster, scale.copy(cpu = Quantity(cpu)))
            }
            else {
              log.debug(s"scale cpu not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.cpu}")
              None
            }

          case ScaleMemoryEscalation(_, _, minimum, maximum, scaleBy, _) ⇒
            val memory = if (escalate) scale.memory.value + scaleBy else scale.memory.value - scaleBy
            if (memory <= maximum && memory >= minimum) {
              log.info(s"scale memory: ${deployment.name}/${targetCluster.name} to $memory")
              commit(targetCluster, scale.copy(memory = MegaByte(memory)))
            }
            else {
              log.debug(s"scale memory not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.memory}")
              None
            }
        }
    }
  }
}

