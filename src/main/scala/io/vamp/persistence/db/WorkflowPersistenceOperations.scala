package io.vamp.persistence.db

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact._
import io.vamp.model.reader.WorkflowStatusReader

import scala.concurrent.Future

trait WorkflowPersistenceMessages {

  case class UpdateWorkflowStatus(workflow: Workflow, status: Workflow.Status) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowScale(workflow: Workflow, scale: DefaultScale) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowNetwork(workflow: Workflow, network: String) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowArguments(workflow: Workflow, arguments: List[Argument]) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowEnvironmentVariables(workflow: Workflow, environmentVariables: List[EnvironmentVariable]) extends PersistenceActor.PersistenceMessages

  case class UpdateWorkflowInstances(workflow: Workflow, instances: List[Instance]) extends PersistenceActor.PersistenceMessages

  case class ResetWorkflow(workflow: Workflow) extends PersistenceActor.PersistenceMessages

}

trait WorkflowPersistenceOperations {
  this: CommonSupportForActors ⇒

  import PersistenceActor._

  implicit def timeout: Timeout

  def receive: Actor.Receive = {

    case o: UpdateWorkflowStatus               ⇒ updateWorkflowStatus(o.workflow, o.status)

    case o: UpdateWorkflowScale                ⇒ updateWorkflowScale(o.workflow, o.scale)

    case o: UpdateWorkflowNetwork              ⇒ updateWorkflowNetwork(o.workflow, o.network)

    case o: UpdateWorkflowArguments            ⇒ updateWorkflowArguments(o.workflow, o.arguments)

    case o: UpdateWorkflowEnvironmentVariables ⇒ updateWorkflowEnvironmentVariables(o.workflow, o.environmentVariables)

    case o: UpdateWorkflowInstances            ⇒ updateWorkflowInstances(o.workflow, o.instances)

    case o: ResetWorkflow                      ⇒ resetWorkflow(o.workflow)
  }

  private def updateWorkflowStatus(workflow: Workflow, status: Workflow.Status) = reply {
    val message = status match {
      case Workflow.Status.Restarting(phase) ⇒ WorkflowStatus(workflow.name, status.toString, phase.map(_.toString))
      case _                                 ⇒ WorkflowStatus(workflow.name, status.toString, None)
    }
    self ? PersistenceActor.Update(message)
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

  private def updateWorkflowEnvironmentVariables(workflow: Workflow, environmentVariables: List[EnvironmentVariable]) = reply {
    self ? PersistenceActor.Update(WorkflowEnvironmentVariables(workflow.name, environmentVariables))
  }

  private def updateWorkflowInstances(workflow: Workflow, instances: List[Instance]) = reply {
    self ? PersistenceActor.Update(WorkflowInstances(workflow.name, instances))
  }

  private def resetWorkflow(workflow: Workflow) = reply {
    val messages = PersistenceActor.Delete(workflow.name, classOf[WorkflowStatus]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowScale]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowNetwork]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowArguments]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowEnvironmentVariables]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowInstances]) :: Nil
    Future.sequence(messages.map(self ? _))
  }
}

private[persistence] case class WorkflowStatus(name: String, status: String, phase: Option[String]) extends Artifact {
  val kind = "workflow-status"

  def unmarshall = WorkflowStatusReader.status(status) match {
    case r: Workflow.Status.Restarting ⇒ r.copy(phase = WorkflowStatusReader.phase(phase))
    case other                         ⇒ other
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

private[persistence] case class WorkflowEnvironmentVariables(name: String, environmentVariables: List[EnvironmentVariable]) extends Artifact {
  val kind = "workflow-environment-variables"
}

private[persistence] case class WorkflowInstances(name: String, instances: List[Instance]) extends Artifact {
  val kind = "workflow-instances"
}
