package io.vamp.persistence.db

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact._
import io.vamp.model.workflow.Workflow

object WorkflowPersistenceMessages extends WorkflowPersistenceMessages

trait WorkflowPersistenceMessages {

  case class UpdateWorkflowScale(workflow: Workflow, scale: DefaultScale) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowNetwork(workflow: Workflow, network: String) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowArguments(workflow: Workflow, arguments: List[Argument]) extends PersistenceActor.PersistenceMessages

}

trait WorkflowPersistenceOperations {
  this: CommonSupportForActors ⇒

  import WorkflowPersistenceMessages._

  implicit def timeout: Timeout

  protected def receiveWorkflow: Actor.Receive = {

    case o: UpdateWorkflowScale   ⇒ updateWorkflowScale(o.workflow, o.scale)

    case o: UpdateWorkflowNetwork   ⇒ updateWorkflowNetwork(o.workflow, o.network)

    case o: UpdateWorkflowArguments ⇒ updateWorkflowArguments(o.workflow, o.arguments)
  }

  private def updateWorkflowScale(workflow: Workflow, scale: DefaultScale) = reply {
    self ? PersistenceActor.Update(WorkflowScale(workflow.name, scale))
  }

  private def updateWorkflowNetwork(workflow: Workflow, network: String) = reply {
    self ? PersistenceActor.Update(WorkflowNetwork(workflow.name, network))
  }

  private def updateWorkflowArguments(workflow: Workflow, arguments: List[Argument]) = reply {
    self ? PersistenceActor.Update(WorkflowArguments(workflow.name, arguments))
  }
}

private[persistence] case class WorkflowScale(name: String, scale: DefaultScale) extends Artifact {
  val kind = "workflow-scale"
}

private[persistence] case class WorkflowNetwork(name: String, network: String) extends Artifact {
  val kind = "workflow-network"
}

private[persistence] case class WorkflowArguments(name: String, arguments: List[Argument]) extends Artifact {
  val kind = "workflow-arguments"
}
