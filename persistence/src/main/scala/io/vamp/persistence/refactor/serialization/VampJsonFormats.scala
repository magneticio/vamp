package io.vamp.persistence.refactor.serialization

import io.vamp.common._
import io.vamp.model.artifact._
import spray.json._

/**
 * Created by mihai on 11/10/17.
 */
trait VampJsonFormats extends DefaultJsonProtocol with VampJsonDecoders with VampJsonEncoders{

  implicit val environmentVariableSerilizationSpecifier: SerializationSpecifier[EnvironmentVariable] =
    SerializationSpecifier[EnvironmentVariable](environmentVariableEncoder, environmentVariableDecoder, "envVar", (e ⇒ Id[EnvironmentVariable](e.name)))

  implicit val gatewaySerilizationSpecifier: SerializationSpecifier[Gateway] =
    SerializationSpecifier[Gateway](gatewayEncoder, gatewayDecoder, "gateway", (e ⇒ Id[Gateway](e.name)))

  implicit val workflowSerilizationSpecifier: SerializationSpecifier[Workflow] =
    SerializationSpecifier[Workflow](workflowEncoder, workflowDecoder, "workflow", (e ⇒ Id[Workflow](e.name)))

  implicit val blueprintSerilizationSpecifier: SerializationSpecifier[Blueprint] =
    SerializationSpecifier[Blueprint](blueprintEncoder, blueprintDecoder, "blueprint", (e ⇒ Id[Blueprint](e.name)))


}


