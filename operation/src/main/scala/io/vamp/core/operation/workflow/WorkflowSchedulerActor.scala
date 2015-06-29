package io.vamp.core.operation.workflow

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.core.model.workflow.{DeploymentTrigger, EventTrigger, ScheduledWorkflow, TimeTrigger}
import io.vamp.core.operation.notification._
import io.vamp.core.persistence.{ArtifactSupport, PersistenceActor}
import io.vamp.core.pulse.PulseActor
import io.vamp.core.pulse.PulseActor.{UnregisterPercolator, RegisterPercolator}

import scala.language.postfixOps

object WorkflowSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[WorkflowSchedulerActor]

  case class Schedule(scheduledWorkflow: ScheduledWorkflow)

  case class Unschedule(scheduledWorkflow: ScheduledWorkflow)

  case class RunWorkflow(scheduledWorkflow: ScheduledWorkflow)

}

class WorkflowSchedulerActor extends WorkflowQuartzScheduler with WorkflowExecutor with ArtifactSupport with CommonSupportForActors with OperationNotificationProvider {
  
  import WorkflowSchedulerActor._

  def receive: Receive = {
    case Start => try start(()) catch {
      case t: Throwable => reportException(InternalServerError(t))
    }

    case Schedule(workflow) => try schedule(workflow) catch {
      case t: Throwable => reportException(WorkflowSchedulingError(t))
    }

    case Unschedule(workflow) => try unschedule(workflow) catch {
      case t: Throwable => reportException(WorkflowSchedulingError(t))
    }

    case (RunWorkflow(workflow), data) => try execute(workflow, data) catch {
      case t: Throwable => reportException(WorkflowExecutionError(t))
    }

    case Shutdown => try shutdown(()) catch {
      case t: Throwable => reportException(InternalServerError(t))
    }

    case _ =>
  }

  private def start: (Unit => Unit) = quartzStart andThen { _ =>
    implicit val timeout = PersistenceActor.timeout
    offload(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[ScheduledWorkflow])) match {
      case scheduledWorkflows: List[_] =>
        scheduledWorkflows.asInstanceOf[List[ScheduledWorkflow]].foreach(scheduledWorkflow => self ! Schedule(scheduledWorkflow))
      case any => reportException(InternalServerError(any))
    }
  }

  private def shutdown: (Unit => Unit) = quartzShutdown

  private def schedule: (ScheduledWorkflow => Unit) = { (workflow: ScheduledWorkflow) =>
    unschedule(workflow)

    log.debug(s"Scheduling workflow: '${workflow.name}'.")

    workflow.trigger match {
      case TimeTrigger(pattern) =>
        quartzSchedule(workflow)

      case EventTrigger(tags) =>
        actorFor(PulseActor) ! RegisterPercolator(workflow.name, tags, RunWorkflow(workflow))

      case DeploymentTrigger(name) =>
        actorFor(PulseActor) ! RegisterPercolator(workflow.name, Set("deployments", s"deployments:$name"), RunWorkflow(workflow))

      case trigger =>
        log.warning(s"Unsupported trigger: '$trigger'.")
    }
  }

  private def unschedule: (ScheduledWorkflow => Unit) = { (workflow: ScheduledWorkflow) =>
    log.debug(s"Unscheduling workflow: '${workflow.name}'.")
    actorFor(PulseActor) ! UnregisterPercolator(workflow.name)
    quartzUnschedule(workflow)
  }
}

