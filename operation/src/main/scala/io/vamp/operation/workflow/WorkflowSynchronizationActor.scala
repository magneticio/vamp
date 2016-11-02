package io.vamp.operation.workflow

import io.vamp.common.akka._
import io.vamp.model.artifact.{ DaemonSchedule, TimeSchedule, Workflow }
import io.vamp.operation.notification._
import io.vamp.operation.workflow.WorkflowActor.Update
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
    case Scheduled(scheduled, instance) ⇒ if (instance.isEmpty) IoC.actorFor[WorkflowActor] ! Update(scheduled)
    case _                              ⇒
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    forAll[Workflow](allArtifacts[Workflow], {
      workflows ⇒
        IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.GetScheduled(
          self,
          workflows.filter(workflow ⇒ workflow.schedule.isInstanceOf[TimeSchedule] || workflow.schedule == DaemonSchedule)
        )
    })
  }
}
