package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{ Id, Namespace }
import io.vamp.model.artifact._
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.model.reader.WorkflowStatusReader
import io.vamp.operation.notification.{ DeploymentWorkflowNameCollision, WorkflowUpdateError }
import io.vamp.persistence.ArtifactExpansionSupport
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

trait WorkflowApiController extends AbstractController with VampJsonFormats {
  this: ArtifactExpansionSupport ⇒

  protected def createWorkflow(artifact: Workflow, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    updateWorkflow(artifact, validateOnly, create = true)
  }

  protected def updateWorkflow(artifact: Workflow, name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    if (name != artifact.name)
      throwException(InconsistentArtifactName(name, artifact.name))
    updateWorkflow(artifact, validateOnly, create = false)
  }

  private def updateWorkflow(artifact: Workflow, validateOnly: Boolean, create: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    VampPersistence().readIfAvailable[Deployment](Id[Deployment](artifact.name)).flatMap {
      case Some(_) ⇒ throwException(DeploymentWorkflowNameCollision(artifact.name))
      case _ ⇒ VampPersistence().readIfAvailable[Workflow](Id[Workflow](artifact.name)).flatMap {
        case Some(workflow) if workflow.status != Workflow.Status.Suspended ⇒ throwException(WorkflowUpdateError(workflow))
        case _ ⇒
          if (validateOnly)
            Future.successful(Some(artifact))
          else if (create) VampPersistence().create(artifact)
          else VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(artifact), _ ⇒ artifact)
      }
    } map {
      case list: List[_] ⇒
        if (!validateOnly) list.foreach {
          case workflow: Workflow ⇒ VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(
            scale = None,
            arguments = Nil, network = None, environmentVariables = Nil)
          )
        }
        list
      case any ⇒ any
    }
  }

  protected def deleteWorkflow(read: Future[Any], validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    read.flatMap {
      case Some(workflow: Workflow) ⇒
        if (validateOnly) Future.successful(true) else VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Stopping))
      case _ ⇒ Future.successful(false)
    }
  }

  protected def workflowStatus(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[String] = {
    VampPersistence().read(Id[Workflow](name)).map(_.status.toString)
  }

  def workflowStatusUpdate(name: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    val newStatus = WorkflowStatusReader.status(request)
    if (validateOnly) Future.successful(newStatus.toString)
    else
      for {
        _ ← VampPersistence().update[Workflow](Id[Workflow](name), _.copy(status = newStatus))
      } yield ()
  }
}
