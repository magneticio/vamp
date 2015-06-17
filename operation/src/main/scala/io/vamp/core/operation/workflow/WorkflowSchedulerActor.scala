package io.vamp.core.operation.workflow

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.core.model.workflow.{ScheduledWorkflow, TimeTrigger}
import io.vamp.core.operation.notification._
import io.vamp.core.persistence.actor.{ArtifactSupport, PersistenceActor}

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

    case Start => handle(start)

    case Schedule(workflow) => handle(workflow, schedule)

    case Unschedule(workflow) => handle(workflow, unschedule)

    case RunWorkflow(workflow) => handle(workflow, execute)

    case Shutdown => handle(shutdown)

    case _ =>
  }

  private def handle(callback: (Unit => Unit)): Unit = try {
    callback(())
  } catch {
    case t: Throwable => exception(InternalServerError(t))
  }

  private def handle(scheduledWorkflow: ScheduledWorkflow, callback: ScheduledWorkflow => Unit): Unit = try {
    callback(scheduledWorkflow)
  } catch {
    case t: Throwable => exception(WorkflowSchedulingError(t))
  }

  private def start: (Unit => Unit) = quartzStart andThen { _ =>
    implicit val timeout = PersistenceActor.timeout
    offload(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[ScheduledWorkflow])) match {
      case scheduledWorkflows: List[_] =>
        scheduledWorkflows.asInstanceOf[List[ScheduledWorkflow]].foreach(scheduledWorkflow => self ! Schedule(scheduledWorkflow))
      case any => exception(InternalServerError(any))
    }
  }

  private def shutdown: (Unit => Unit) = quartzShutdown

  private def schedule: (ScheduledWorkflow => Unit) = { (scheduledWorkflow: ScheduledWorkflow) =>
    scheduledWorkflow.trigger match {
      case TimeTrigger(pattern) => quartzSchedule(scheduledWorkflow)
      case trigger => log.warning(s"Unsupported trigger: $trigger")
    }
  }

  private def unschedule: (ScheduledWorkflow => Unit) = { (scheduledWorkflow: ScheduledWorkflow) =>
    scheduledWorkflow.trigger match {
      case TimeTrigger(_) => quartzUnschedule(scheduledWorkflow)
      case trigger => log.warning(s"Unsupported trigger: $trigger")
    }
  }
}


