package io.vamp.operation.workflow

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact.Workflow.Status.RestartingPhase
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.operation.notification._
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.{ ArtifactSupport }
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }
import io.vamp.workflow_driver.WorkflowDriverActor

import scala.concurrent.Future

object WorkflowActor {

  case class Update(workflow: Workflow, running: Boolean)

  case class Trigger(workflow: Workflow)

}

class WorkflowActor extends ArtifactSupport with CommonSupportForActors with OperationNotificationProvider with VampJsonFormats {

  import PulseEventTags.Workflows._
  import WorkflowActor._

  implicit val timeout = WorkflowDriverActor.timeout()

  def receive: Receive = {

    case Update(workflow, running) ⇒ try update(workflow, running) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case (Trigger(workflow), event: Event) ⇒ try trigger(workflow, event) catch {
      case t: Throwable ⇒ reportException(WorkflowExecutionError(t))
    }

    case _ ⇒
  }

  private def update(workflow: Workflow, running: Boolean): Unit = workflow.status match {

    case Workflow.Status.Starting      ⇒ run(workflow, running)

    case Workflow.Status.Stopping      ⇒ stop(workflow, running)

    case Workflow.Status.Running       ⇒ run(workflow, running)

    case Workflow.Status.Suspended     ⇒ suspend(workflow, running)

    case Workflow.Status.Suspending    ⇒ suspend(workflow, running)

    case _: Workflow.Status.Restarting ⇒ restart(workflow, running)
  }

  private def run(workflow: Workflow, running: Boolean): Unit = {
    deploy(workflow, running, () ⇒ {
      if (workflow.status != Workflow.Status.Running)
        (VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Running))).map { _ ⇒
          pulse(workflow, scheduled = true)
        }
    })
  }

  private def stop(workflow: Workflow, running: Boolean): Unit = {
    undeploy(workflow, running, () ⇒ {
      (for {
        _ ← VampPersistence().deleteObject(workflowSerilizationSpecifier.idExtractor(workflow))
      } yield pulse(workflow, scheduled = false)
      )
    })
  }

  private def suspend(workflow: Workflow, running: Boolean): Unit = {
    undeploy(workflow, running, () ⇒ {
      if (workflow.status != Workflow.Status.Suspended) {
        (VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Suspended))).map { _ ⇒
          pulse(workflow, scheduled = false)
        }
      }
      else (VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(health = None, instances = Nil)))
    })
  }

  private def restart(workflow: Workflow, running: Boolean): Unit = {
    workflow.status match {
      case Workflow.Status.Restarting(Some(RestartingPhase.Starting)) ⇒
        deploy(workflow, running, () ⇒ {
          (VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Running))).map { _ ⇒
            pulse(workflow, scheduled = true)
          }
        })
      case _ ⇒
        undeploy(workflow, running, () ⇒ {
          (VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Restarting(
            phase = Some(Workflow.Status.RestartingPhase.Starting))))
          ).map { _ ⇒
            pulse(workflow, scheduled = false)
          }
        })
    }
  }

  private def deploy(workflow: Workflow, running: Boolean, update: () ⇒ Unit) = {
    if (running) update()
    else workflow.schedule match {
      case DaemonSchedule        ⇒ trigger(workflow)
      case TimeSchedule(_, _, _) ⇒ trigger(workflow)
      case EventSchedule(tags)   ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(WorkflowDriverActor.percolator(workflow), tags, None, Trigger(workflow))
      case _                     ⇒
    }

  }

  private def undeploy(workflow: Workflow, running: Boolean, update: () ⇒ Unit) = {
    if (!running) update()
    else workflow.schedule match {
      case DaemonSchedule        ⇒ if (running) IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.Unschedule(workflow) else update()
      case TimeSchedule(_, _, _) ⇒ if (running) IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.Unschedule(workflow) else update()
      case EventSchedule(_)      ⇒ IoC.actorFor[PulseActor] ! UnregisterPercolator(WorkflowDriverActor.percolator(workflow))
      case _                     ⇒
    }
  }

  private def trigger(workflow: Workflow, data: Any = None): Future[_] = {
    log.info(s"Triggering workflow: '${workflow.name}'.")
    IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Schedule(workflow, data)
  }

  private def pulse(workflow: Workflow, scheduled: Boolean) = {
    actorFor[PulseActor] ! Publish(Event(Set(s"workflows${Event.tagDelimiter}${workflow.name}", if (scheduled) scheduledTag else unscheduledTag), workflow), publishEventValue = false)
  }
}
