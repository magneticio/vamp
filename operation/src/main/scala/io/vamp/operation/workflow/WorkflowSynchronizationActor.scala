package io.vamp.operation.workflow

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.model.workflow.{ ScheduledWorkflow, TimeTrigger }
import io.vamp.operation.notification._
import io.vamp.operation.workflow.WorkflowSynchronizationActor.Synchronize
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.workflow_driver.{ WorkflowDriverActor, WorkflowInstance }

import scala.language.postfixOps

class WorkflowSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[WorkflowSynchronizationActor] ! Synchronize
}

object WorkflowSynchronizationActor {

  sealed trait WorkflowMessages

  object Synchronize extends WorkflowMessages

}

class WorkflowSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import WorkflowSynchronizationActor._

  def receive = {
    case Synchronize ⇒ synchronize()
    case _           ⇒
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    for {
      workflows ← allArtifacts[ScheduledWorkflow]
      scheduled ← checked[List[WorkflowInstance]](IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Scheduled)
    } yield {
      workflows.filter {
        _.trigger.isInstanceOf[TimeTrigger]
      } filterNot {
        workflow ⇒ scheduled.exists(_.name == workflow.name)
      } foreach {
        workflow ⇒ IoC.actorFor[WorkflowActor] ! WorkflowActor.Schedule(workflow)
      }
    }
  }
}
