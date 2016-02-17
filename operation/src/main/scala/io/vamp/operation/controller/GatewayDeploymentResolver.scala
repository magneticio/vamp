package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, IoC, ReplyActor }
import io.vamp.model.artifact.{ Deployment, Gateway }
import io.vamp.persistence.db.PersistenceActor

import scala.concurrent.Future

trait GatewayDeploymentResolver {
  this: ReplyActor with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  protected def gatewayFor(name: String): Future[Option[Gateway]] = checked[Option[Gateway]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Gateway]))

  protected def deploymentFor(name: String): Future[Option[Deployment]] = checked[Option[Deployment]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment]))
}
