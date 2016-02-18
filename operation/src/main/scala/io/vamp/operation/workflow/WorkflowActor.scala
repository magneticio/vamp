package io.vamp.operation.workflow

import akka.actor._
import io.vamp.common.akka._
import io.vamp.model.event.Event
import io.vamp.model.workflow.{ DeploymentTrigger, EventTrigger, ScheduledWorkflow, TimeTrigger }
import io.vamp.operation.OperationBootstrap
import io.vamp.operation.notification._
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor
import io.vamp.workflow_driver.WorkflowDriverActor
import akka.pattern.ask

import scala.concurrent.Future
import scala.language.postfixOps

object WorkflowActor {

  object RescheduleAll

  case class Schedule(workflow: ScheduledWorkflow)

  case class Unschedule(workflow: ScheduledWorkflow)

  case class RunWorkflow(workflow: ScheduledWorkflow)

}

class WorkflowActor extends ArtifactPaginationSupport with ArtifactSupport with CommonSupportForActors with OperationNotificationProvider {

  import WorkflowActor._

  private val percolator = "workflow://"

  implicit val timeout = WorkflowDriverActor.timeout

  def receive: Receive = {

    case RescheduleAll ⇒ try reschedule() catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Schedule(workflow) ⇒ try schedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Unschedule(workflow) ⇒ try unschedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case (RunWorkflow(workflow), event: Event) ⇒ try trigger(workflow, event.tags) catch {
      case t: Throwable ⇒ reportException(WorkflowExecutionError(t))
    }

    case _ ⇒
  }

  override def preStart() = try {
    context.system.scheduler.scheduleOnce(OperationBootstrap.synchronizationInitialDelay, self, RescheduleAll)
  } catch {
    case t: Throwable ⇒ reportException(InternalServerError(t))
  }

  private def reschedule() = {
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[ScheduledWorkflow] map {
      case workflows: List[_] ⇒ workflows.foreach(workflow ⇒ self ! Schedule(workflow))
      case any                ⇒ reportException(InternalServerError(any))
    }
  }

  private def schedule: (ScheduledWorkflow ⇒ Future[Any]) = { workflow ⇒
    unschedule(workflow) flatMap { _ ⇒
      log.debug(s"Scheduling workflow: '${workflow.name}'.")

      workflow.trigger match {
        case TimeTrigger(_, _, _)    ⇒ trigger(workflow)
        case EventTrigger(tags)      ⇒ IoC.actorFor[PulseActor] ? RegisterPercolator(s"$percolator${workflow.name}", tags, RunWorkflow(workflow))
        case DeploymentTrigger(name) ⇒ IoC.actorFor[PulseActor] ? RegisterPercolator(s"$percolator${workflow.name}", Set("deployments", s"deployments:$name"), RunWorkflow(workflow))
        case trigger                 ⇒ Future.successful(log.warning(s"Unsupported trigger: '$trigger'."))
      }
    }
  }

  private def unschedule: (ScheduledWorkflow ⇒ Future[Any]) = { workflow ⇒
    log.debug(s"Unscheduling workflow: '${workflow.name}'.")
    IoC.actorFor[PulseActor] ? UnregisterPercolator(s"$percolator${workflow.name}")
    IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Unschedule(workflow)
  }

  private def trigger(workflow: ScheduledWorkflow, data: Any = None) = {
    IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Schedule(workflow, data)
  }
}
