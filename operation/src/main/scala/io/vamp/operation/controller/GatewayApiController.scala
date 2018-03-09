package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Artifact, Namespace }
import io.vamp.common.akka.IoC.actorFor
import io.vamp.model.artifact.Gateway
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.operation.gateway.GatewayActor
import io.vamp.persistence.ArtifactExpansionSupport

import scala.concurrent.Future

trait GatewayApiController extends AbstractController {
  this: ArtifactExpansionSupport ⇒

  protected def createGateway(artifact: Artifact, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    expandGateway(artifact.asInstanceOf[Gateway]) flatMap { gateway ⇒
      actorFor[GatewayActor] ? GatewayActor.Create(gateway, Option(source), validateOnly)
    }
  }

  protected def updateGateway(artifact: Artifact, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    expandGateway(artifact.asInstanceOf[Gateway]) flatMap { gateway ⇒
      if (name != gateway.name) throwException(InconsistentArtifactName(name, gateway.name))
      actorFor[GatewayActor] ? GatewayActor.Update(gateway, Option(source), validateOnly)
    }
  }

  protected def deleteGateway(name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    actorFor[GatewayActor] ? GatewayActor.Delete(name, validateOnly)
  }
}
