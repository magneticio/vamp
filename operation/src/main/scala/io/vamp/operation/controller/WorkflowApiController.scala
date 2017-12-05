package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{Id, Namespace, UnitPlaceholder}
import io.vamp.model.artifact._
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.model.reader.WorkflowStatusReader
import io.vamp.operation.notification.{DeploymentWorkflowNameCollision, WorkflowUpdateError}
import io.vamp.persistence.ArtifactExpansionSupport
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

trait WorkflowApiController extends AbstractController with VampJsonFormats {
  this: ArtifactExpansionSupport ⇒

  protected def createWorkflow(artifact: Workflow, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Workflow] = {
    createOrUpdateVerifyingConflicts(artifact, validateOnly, create = true)
  }

  protected def updateWorkflow(artifact: Workflow, name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Workflow] = {
    if (name != artifact.name)
      throwException(InconsistentArtifactName(name, artifact.name))
    createOrUpdateVerifyingConflicts(artifact, validateOnly, create = false)
  }

  private def createOrUpdateVerifyingConflicts(artifact: Workflow, validateOnly: Boolean, create: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Workflow] = {
     VampPersistence().readIfAvailable[Deployment](Id[Deployment](artifact.name)).flatMap {
      case Some(_) ⇒ throwException(DeploymentWorkflowNameCollision(artifact.name))
      case _ ⇒ VampPersistence().readIfAvailable[Workflow](Id[Workflow](artifact.name)).flatMap {
        case Some(workflow) if workflow.status != Workflow.Status.Suspended ⇒ throwException(WorkflowUpdateError(workflow))
        case _ ⇒
          if (validateOnly)
            Future.successful(artifact)
          else if (create) VampPersistence().createOrUpdate[Workflow](artifact).flatMap(_ => VampPersistence().read[Workflow](workflowSerilizationSpecifier.idExtractor(artifact)))
          else VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(artifact), _ ⇒ artifact).flatMap(_ =>
            VampPersistence().read[Workflow](workflowSerilizationSpecifier.idExtractor(artifact))
          )
      }
    }
    /* In the old version of persistence, in some cases it appears the create/update operation returned a list.
       .map {
      case list: List[_] ⇒
        if (!validateOnly) list.foreach {
          case workflow: Workflow ⇒ VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(
            scale = None,
            arguments = Nil, network = None, environmentVariables = Nil)
          )
        }
        list
      case any ⇒ any
    }*/
  }

  protected def workflowStatus(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[String] = {
    VampPersistence().read(Id[Workflow](name)).map(_.status.toString)
  }

  def workflowStatusUpdate(name: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] = {
    val newStatus = WorkflowStatusReader.status(request)
    if (validateOnly) Future.successful(UnitPlaceholder)
    else VampPersistence().update[Workflow](Id[Workflow](name), _.copy(status = newStatus)).map(_ => UnitPlaceholder)
  }
}
