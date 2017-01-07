package io.vamp.operation.workflow

import io.vamp.common.akka._
import io.vamp.model.artifact.Workflow
import io.vamp.operation.notification._
import io.vamp.operation.workflow.WorkflowActor.Update
import io.vamp.operation.workflow.WorkflowSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.workflow_driver.WorkflowDriverActor

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
    case SynchronizeAll ⇒ synchronize()
    case _              ⇒
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout()
    forAll[Workflow](allArtifacts[Workflow], {
      workflows ⇒
        IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.GetScheduled(workflows)
        workflows.foreach { workflow ⇒ IoC.actorFor[WorkflowActor] ! Update(workflow, running = workflow.instances.nonEmpty) }
    })
  }
}
