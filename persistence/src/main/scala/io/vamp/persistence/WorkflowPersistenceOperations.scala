package io.vamp.persistence

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC.actorFor
import io.vamp.model.artifact._
import io.vamp.model.reader.WorkflowStatusReader

import scala.concurrent.Future

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
  this: CommonSupportForActors ⇒

  import PersistenceActor._

  implicit def timeout: Timeout

  def receive: Actor.Receive = {

    case o: UpdateWorkflowBreed                ⇒ updateWorkflowBreed(o.workflow, o.breed)

    case o: UpdateWorkflowStatus               ⇒ updateWorkflowStatus(o.workflow, o.status)

    case o: UpdateWorkflowScale                ⇒ updateWorkflowScale(o.workflow, o.scale)

    case o: UpdateWorkflowNetwork              ⇒ updateWorkflowNetwork(o.workflow, o.network)

    case o: UpdateWorkflowArguments            ⇒ updateWorkflowArguments(o.workflow, o.arguments)

    case o: UpdateWorkflowEnvironmentVariables ⇒ updateWorkflowEnvironmentVariables(o.workflow, o.environmentVariables)

    case o: UpdateWorkflowInstances            ⇒ updateWorkflowInstances(o.workflow, o.instances)

    case o: UpdateWorkflowHealth               ⇒ updateWorkflowHealth(o.workflow, o.health)

    case o: ResetWorkflow                      ⇒ resetWorkflow(o.workflow, o.runtime, o.attributes)
  }

  private def updateWorkflowBreed(workflow: Workflow, breed: DefaultBreed) = reply {
    self ? PersistenceActor.Update(WorkflowBreed(workflow.name, breed))
  }

  private def updateWorkflowStatus(workflow: Workflow, status: Workflow.Status) = reply {
    val message = status match {
      case Workflow.Status.Restarting(phase) ⇒ WorkflowStatus(workflow.name, status.toString, phase.map(_.toString))
      case _                                 ⇒ WorkflowStatus(workflow.name, status.toString, None)
    }
    // TODO: change log info to debug level
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(message.name, classOf[WorkflowStatus])) map {
      case Some(currentStatus: WorkflowStatus) ⇒ if (currentStatus != message) {
        log.info("Workflow Status changed, writing to db")
        self ? PersistenceActor.Update(message, Option(status.describe))
      }
      else {
        log.info("Workflow Status hasn't changed, not writing to db")
      }
      case _ ⇒ {
        log.info("Workflow Status does not exist, writing to db")
        self ? PersistenceActor.Update(message, Option(status.describe))
      }
    }
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

  private def updateWorkflowHealth(workflow: Workflow, health: Option[Health]) = reply {
    health match {
      case Some(h: Health) ⇒
        // TODO: change log info to debug level
        checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(workflow.name, classOf[WorkflowHealth])) map {
          case Some(currentHealth: WorkflowHealth) ⇒ if (!currentHealth.health.contains(h)) {
            log.info("Workflow Health changed, writing to db")
            self ? PersistenceActor.Update(WorkflowHealth(workflow.name, health))
          }
          else {
            log.info("Workflow Health hasn't changed, not writing to db")
          }
          case _ ⇒ {
            log.info("Workflow Health does not exist, writing to db")
            self ? PersistenceActor.Update(WorkflowHealth(workflow.name, health))
          }
        }
      case None ⇒ {
        log.warning("Updating health with None")
        self ? PersistenceActor.Update(WorkflowHealth(workflow.name, health))
      }
    }
  }

  private def resetWorkflow(workflow: Workflow, runtime: Boolean, attributes: Boolean) = reply {

    val attributeArtifacts = if (attributes) {
      PersistenceActor.Delete(workflow.name, classOf[WorkflowStatus]) ::
        PersistenceActor.Delete(workflow.name, classOf[WorkflowScale]) ::
        PersistenceActor.Delete(workflow.name, classOf[WorkflowNetwork]) ::
        PersistenceActor.Delete(workflow.name, classOf[WorkflowArguments]) ::
        PersistenceActor.Delete(workflow.name, classOf[WorkflowEnvironmentVariables]) :: Nil
    }
    else Nil

    val runtimeArtifacts = if (runtime) PersistenceActor.Delete(workflow.name, classOf[WorkflowBreed]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowHealth]) ::
      PersistenceActor.Delete(workflow.name, classOf[WorkflowInstances]) :: Nil
    else Nil

    Future.sequence((attributeArtifacts ++ runtimeArtifacts).map(self ? _))
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

