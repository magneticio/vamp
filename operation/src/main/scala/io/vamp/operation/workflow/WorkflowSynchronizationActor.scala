package io.vamp.operation.workflow

import io.vamp.common.akka._
import io.vamp.model.workflow.{ DaemonTrigger, ScheduledWorkflow, TimeTrigger }
import io.vamp.operation.notification._
import io.vamp.operation.workflow.WorkflowActor.Schedule
import io.vamp.operation.workflow.WorkflowSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.workflow_driver.WorkflowDriverActor
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled

class WorkflowSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[WorkflowSynchronizationActor] ! SynchronizeAll
}

object WorkflowSynchronizationActor {

  sealed trait WorkflowMessages

  object SynchronizeAll extends WorkflowMessages

}

class WorkflowSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import WorkflowSynchronizationActor._

  def receive = {
    case SynchronizeAll                 ⇒ synchronize()
    case Scheduled(scheduled, instance) ⇒ if (instance.isEmpty) IoC.actorFor[WorkflowActor] ! Schedule(scheduled)
    case _                              ⇒
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[ScheduledWorkflow] map { scheduledWorkflows ⇒

      val workflows = scheduledWorkflows.filter {
        scheduled ⇒ scheduled.trigger.isInstanceOf[TimeTrigger] || scheduled.trigger == DaemonTrigger
      }

      IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.GetScheduled(self, workflows)
    }
  }
}
