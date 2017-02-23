package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ CommonProvider, IoC, ReplyCheck }
import io.vamp.model.artifact.{ Deployment, Gateway, Workflow }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future

trait GatewayWorkflowDeploymentResolver extends ReplyCheck {
  this: CommonProvider ⇒

  implicit def timeout: Timeout

  protected def gatewayFor(name: String): Future[Option[Gateway]] = {
    checked[Option[Gateway]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Gateway]))
  }

  protected def workflowFor(name: String): Future[Option[Workflow]] = {
    checked[Option[Workflow]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Workflow]))
  }

  protected def deploymentFor(name: String): Future[Option[Deployment]] = {
    checked[Option[Deployment]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment]))
  }
}
