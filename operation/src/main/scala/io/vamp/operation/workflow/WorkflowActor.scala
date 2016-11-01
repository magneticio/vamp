package io.vamp.operation.workflow

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.operation.OperationBootstrap
import io.vamp.operation.notification._
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }
import io.vamp.workflow_driver.{ WorkflowDeployable, WorkflowDriver, WorkflowDriverActor }

import scala.concurrent.Future

object WorkflowActor {

  object RescheduleAll

  case class Schedule(workflow: Workflow)

  case class Unschedule(workflow: Workflow)

  case class RunWorkflow(workflow: Workflow)

}

class WorkflowActor extends ArtifactPaginationSupport with ArtifactSupport with CommonSupportForActors with OperationNotificationProvider {

  import PulseEventTags.Workflows._
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
  }
  catch {
    case t: Throwable ⇒ reportException(InternalServerError(t))
  }

  private def reschedule() = {
    implicit val timeout = PersistenceActor.timeout
    forEach[Workflow](allArtifacts[Workflow], {
      workflow ⇒ self ! Schedule(workflow)
    })
  }

  private def schedule(workflow: Workflow): Unit = {
    log.info(s"Scheduling workflow: '${workflow.name}'.")

    workflow.schedule match {
      case DaemonSchedule        ⇒ trigger(workflow)
      case TimeSchedule(_, _, _) ⇒ trigger(workflow)
      case EventSchedule(tags)   ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(s"$percolator${workflow.name}", tags, RunWorkflow(workflow))
      case schedule              ⇒ log.warning(s"Unsupported schedule: '$schedule'.")
    }

    pulse(workflow, scheduled = true)
  }

  private def unschedule(workflow: Workflow) = {
    log.info(s"Unscheduling workflow: '${workflow.name}'.")

    IoC.actorFor[PulseActor] ! UnregisterPercolator(s"$percolator${workflow.name}")
    IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Unschedule(workflow) foreach { _ ⇒ pulse(workflow, scheduled = false) }
  }

  private def trigger(workflow: Workflow, data: Any = None) = for {
    breed ← artifactFor[DefaultBreed](workflow.breed)
    scale ← if (workflow.scale.isDefined) artifactFor[DefaultScale](workflow.scale.get).map(Option(_)) else Future.successful(None)
  } yield {

    if (WorkflowDeployable.matches(breed.deployable)) {
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(WorkflowDriver.path(workflow), Option(breed.deployable.definition)) map {
        _ ⇒ IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.Schedule(workflow.copy(breed = breed, scale = scale), data)
      }
    }
    else {
      IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.Schedule(workflow.copy(breed = breed, scale = scale), data)
    }
  }

  private def pulse(workflow: Workflow, scheduled: Boolean) = {
    actorFor[PulseActor] ! Publish(Event(Set(s"workflows${Event.tagDelimiter}${workflow.name}", if (scheduled) scheduledTag else unscheduledTag), workflow), publishEventValue = false)
  }
}
