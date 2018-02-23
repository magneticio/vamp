package io.vamp.persistence

import akka.actor.Actor
import io.vamp.model.artifact._
import io.vamp.model.reader.WorkflowStatusReader

trait WorkflowPersistenceMessages {

  case class UpdateWorkflowBreed(workflow: Workflow, breed: DefaultBreed) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowStatus(workflow: Workflow, status: Workflow.Status) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowScale(workflow: Workflow, scale: DefaultScale) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowNetwork(workflow: Workflow, network: String) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowArguments(workflow: Workflow, arguments: List[Argument]) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowEnvironmentVariables(workflow: Workflow, environmentVariables: List[EnvironmentVariable]) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowInstances(workflow: Workflow, instances: List[Instance]) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowHealth(workflow: Workflow, health: Option[Health]) extends PersistenceActor.PersistenceMessages

  case class ResetWorkflow(workflow: Workflow, runtime: Boolean, attributes: Boolean) extends PersistenceActor.PersistenceMessages

}

trait WorkflowPersistenceOperations {
  this: PatchPersistenceOperations ⇒

  import PersistenceActor._

  def receive: Actor.Receive = {

    case o: UpdateWorkflowBreed                ⇒ replyUpdate(WorkflowBreed(o.workflow.name, o.breed))

    case o: UpdateWorkflowScale                ⇒ replyUpdate(WorkflowScale(o.workflow.name, o.scale))

    case o: UpdateWorkflowNetwork              ⇒ replyUpdate(WorkflowNetwork(o.workflow.name, o.network))

    case o: UpdateWorkflowArguments            ⇒ replyUpdate(WorkflowArguments(o.workflow.name, o.arguments))

    case o: UpdateWorkflowEnvironmentVariables ⇒ replyUpdate(WorkflowEnvironmentVariables(o.workflow.name, o.environmentVariables))

    case o: UpdateWorkflowInstances            ⇒ replyUpdate(WorkflowInstances(o.workflow.name, o.instances))

    case o: UpdateWorkflowStatus               ⇒ updateWorkflowStatus(o.workflow, o.status)

    case o: UpdateWorkflowHealth               ⇒ replyUpdate(WorkflowHealth(o.workflow.name, o.health))

    case o: ResetWorkflow                      ⇒ resetWorkflow(o.workflow, o.runtime, o.attributes)
  }

  private def updateWorkflowStatus(workflow: Workflow, status: Workflow.Status): Unit = {
    val message = status match {
      case Workflow.Status.Restarting(phase) ⇒ WorkflowStatus(workflow.name, status.toString, phase.map(_.toString))
      case _                                 ⇒ WorkflowStatus(workflow.name, status.toString, None)
    }
    replyUpdate(message, "workflow-statuses", status.describe)
  }

  private def resetWorkflow(workflow: Workflow, runtime: Boolean, attributes: Boolean): Unit = {
    val attributeArtifacts = if (attributes) List(
      classOf[WorkflowStatus],
      classOf[WorkflowScale],
      classOf[WorkflowNetwork],
      classOf[WorkflowArguments],
      classOf[WorkflowEnvironmentVariables]
    )
    else Nil

    val runtimeArtifacts = if (runtime) {
      val breedReset = classOf[WorkflowBreed] :: Nil
      val healthReset = workflow.health.map(_ ⇒ classOf[WorkflowHealth] :: Nil).getOrElse(Nil)
      val instancesReset = if (workflow.instances.nonEmpty) classOf[WorkflowInstances] :: Nil else Nil
      breedReset ++ healthReset ++ instancesReset
    }
    else Nil

    (attributeArtifacts ++ runtimeArtifacts).foreach(t ⇒ replyDelete(workflow.name, t))
  }
}

private[persistence] object WorkflowBreed {
  val kind: String = "workflow-breeds"
}

private[persistence] case class WorkflowBreed(name: String, breed: DefaultBreed) extends PersistenceArtifact {
  val kind: String = WorkflowBreed.kind
}

private[persistence] object WorkflowStatus {
  val kind: String = "workflow-statuses"
}

private[persistence] case class WorkflowStatus(name: String, status: String, phase: Option[String]) extends PersistenceArtifact {
  val kind: String = WorkflowStatus.kind

  def unmarshall: Workflow.Status = WorkflowStatusReader.status(status) match {
    case r: Workflow.Status.Restarting ⇒ r.copy(phase = WorkflowStatusReader.phase(phase))
    case other                         ⇒ other
  }
}

private[persistence] object WorkflowScale {
  val kind: String = "workflow-scales"
}

private[persistence] case class WorkflowScale(name: String, scale: DefaultScale) extends PersistenceArtifact {
  val kind: String = WorkflowScale.kind
}

private[persistence] object WorkflowNetwork {
  val kind: String = "workflow-networks"
}

private[persistence] case class WorkflowNetwork(name: String, network: String) extends PersistenceArtifact {
  val kind: String = WorkflowNetwork.kind
}

private[persistence] object WorkflowArguments {
  val kind: String = "workflow-arguments"
}

private[persistence] case class WorkflowArguments(name: String, arguments: List[Argument]) extends PersistenceArtifact {
  val kind: String = WorkflowArguments.kind
}

private[persistence] object WorkflowEnvironmentVariables {
  val kind: String = "workflow-environment-variables"
}

private[persistence] case class WorkflowEnvironmentVariables(name: String, environmentVariables: List[EnvironmentVariable]) extends PersistenceArtifact {
  val kind: String = WorkflowEnvironmentVariables.kind
}

private[persistence] object WorkflowInstances {
  val kind: String = "workflow-instances"
}

private[persistence] case class WorkflowInstances(name: String, instances: List[Instance]) extends PersistenceArtifact {
  val kind: String = WorkflowInstances.kind
}

private[persistence] object WorkflowHealth {
  val kind: String = "workflow-healths"
}

private[persistence] case class WorkflowHealth(name: String, health: Option[Health]) extends PersistenceArtifact {
  val kind: String = WorkflowHealth.kind
}
