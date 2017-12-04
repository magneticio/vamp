package io.vamp.operation.workflow

import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.model.artifact.Workflow
import io.vamp.operation.notification._
import io.vamp.operation.workflow.WorkflowActor.Update
import io.vamp.operation.workflow.WorkflowSynchronizationActor.SynchronizeAll
import io.vamp.persistence.ArtifactSupport
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.workflow_driver.WorkflowDriverActor

import scala.concurrent.duration._
class WorkflowSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[WorkflowSynchronizationActor] ! SynchronizeAll
}

object WorkflowSynchronizationActor {

  sealed trait WorkflowMessages

  object SynchronizeAll extends WorkflowMessages

}

class WorkflowSynchronizationActor extends CommonSupportForActors with ArtifactSupport with OperationNotificationProvider with VampJsonFormats {

  import WorkflowSynchronizationActor._

  def receive = {
    case SynchronizeAll ⇒ synchronize()
    case _              ⇒
  }

  private def synchronize() = {
    implicit val timeout = Timeout(30.second)
    VampPersistence().getAll[Workflow]().map(_.response).map {
      workflows ⇒
        IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.GetScheduled(workflows)
        workflows.foreach { workflow ⇒ IoC.actorFor[WorkflowActor] ! Update(workflow, running = workflow.instances.nonEmpty) }
    }
  }
}
