package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Artifact, Namespace }
import io.vamp.common.akka.IoC.actorFor
import io.vamp.model.artifact.Gateway
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.model.reader.YamlReader
import io.vamp.operation.gateway.GatewayActor
import io.vamp.persistence.ArtifactExpansionSupport

trait GatewayApiController extends AbstractController {
  this: ArtifactExpansionSupport ⇒

  protected def createGateway(reader: YamlReader[_ <: Artifact], source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    expandGateway(reader.read(source).asInstanceOf[Gateway]) flatMap { gateway ⇒
      actorFor[GatewayActor] ? GatewayActor.Create(gateway, Option(source), validateOnly)
    }
  }

  protected def updateGateway(reader: YamlReader[_ <: Artifact], name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    expandGateway(reader.read(source).asInstanceOf[Gateway]) flatMap { gateway ⇒
      if (name != gateway.name) throwException(InconsistentArtifactName(name, gateway.name))
      actorFor[GatewayActor] ? GatewayActor.Update(gateway, Option(source), validateOnly, promote = true)
    }
  }

  protected def deleteGateway(name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    actorFor[GatewayActor] ? GatewayActor.Delete(name, validateOnly)
  }
}
