package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{Id, Namespace}
import io.vamp.common.akka.ReplyCheck
import io.vamp.model.artifact.{Deployment, Gateway, Workflow}
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.exceptions.InvalidObjectIdException
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

trait GatewayWorkflowDeploymentResolver extends ReplyCheck with VampJsonFormats{
  this: AbstractController ⇒

  protected def gatewayFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Gateway]] = {
    VampPersistence().read[Gateway](Id[Gateway](name)).map(r => Some(r)).recover{case e: InvalidObjectIdException[_] => None}
  }

  protected def workflowFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Workflow]] = {
    VampPersistence().read[Workflow](Id[Workflow](name)).map(r => Some(r)).recover{case e: InvalidObjectIdException[_] => None}
  }

  protected def deploymentFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Deployment]] = {
    VampPersistence().read[Deployment](Id[Deployment](name)).map(r => Some(r)).recover{case e: InvalidObjectIdException[_] => None}
  }
}
