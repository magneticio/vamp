package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.{ IoC, ReplyCheck }
import io.vamp.model.artifact.{ Deployment, Gateway, Workflow }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future

trait GatewayWorkflowDeploymentResolver extends ReplyCheck {
  this: AbstractController ⇒

  protected def gatewayFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Gateway]] = {
    checked[Option[Gateway]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Gateway]))
  }

  protected def workflowFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Workflow]] = {
    checked[Option[Workflow]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Workflow]))
  }

  protected def deploymentFor(name: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Deployment]] = {
    checked[Option[Deployment]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment]))
  }
}
