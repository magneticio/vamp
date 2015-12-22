package io.vamp.operation.workflow

import akka.actor._
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.model.event.Event
import io.vamp.model.workflow.{ DeploymentTrigger, EventTrigger, ScheduledWorkflow, TimeTrigger }
import io.vamp.operation.notification._
import io.vamp.persistence.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor

import scala.language.postfixOps

object WorkflowSchedulerActor {

  object RescheduleAll

  case class Schedule(scheduledWorkflow: ScheduledWorkflow)

  case class Unschedule(scheduledWorkflow: ScheduledWorkflow)

  case class RunWorkflow(scheduledWorkflow: ScheduledWorkflow)

}

class WorkflowSchedulerActor extends WorkflowQuartzScheduler with WorkflowExecutor with ArtifactPaginationSupport with ArtifactSupport with CommonSupportForActors with OperationNotificationProvider {

  import WorkflowSchedulerActor._

  private val percolator = "workflow://"

  def receive: Receive = {
    case Start ⇒ try start(()) catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }

    case RescheduleAll ⇒ try reschedule() catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Schedule(workflow) ⇒ try schedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Unschedule(workflow) ⇒ try unschedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case (RunWorkflow(workflow), time: Long) ⇒ try execute(workflow, time) catch {
      case t: Throwable ⇒ reportException(WorkflowExecutionError(t))
    }

    case (RunWorkflow(workflow), event: Event) ⇒ try execute(workflow, event.tags) catch {
      case t: Throwable ⇒ reportException(WorkflowExecutionError(t))
    }

    case Shutdown ⇒ try shutdown(()) catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }

    case _ ⇒
  }

  private def start: (Unit ⇒ Unit) = quartzStart andThen { _ ⇒
    context.system.scheduler.scheduleOnce(PersistenceActor.timeout.duration, self, RescheduleAll)
  }

  private def reschedule() = {
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[ScheduledWorkflow] map {
      case scheduledWorkflows: List[_] ⇒ scheduledWorkflows.foreach(scheduledWorkflow ⇒ self ! Schedule(scheduledWorkflow))
      case any                         ⇒ reportException(InternalServerError(any))
    }
  }

  private def shutdown: (Unit ⇒ Unit) = quartzShutdown

  private def schedule: (ScheduledWorkflow ⇒ Unit) = { (workflow: ScheduledWorkflow) ⇒
    unschedule(workflow)

    log.debug(s"Scheduling workflow: '${workflow.name}'.")

    workflow.trigger match {
      case TimeTrigger(pattern)    ⇒ quartzSchedule(workflow)
      case EventTrigger(tags)      ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(s"$percolator${workflow.name}", tags, RunWorkflow(workflow))
      case DeploymentTrigger(name) ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(s"$percolator${workflow.name}", Set("deployments", s"deployments:$name"), RunWorkflow(workflow))
      case trigger                 ⇒ log.warning(s"Unsupported trigger: '$trigger'.")
    }
  }

  private def unschedule: (ScheduledWorkflow ⇒ Unit) = { (workflow: ScheduledWorkflow) ⇒
    log.debug(s"Unscheduling workflow: '${workflow.name}'.")
    IoC.actorFor[PulseActor] ! UnregisterPercolator(s"$percolator${workflow.name}")
    quartzUnschedule(workflow)
  }
}
