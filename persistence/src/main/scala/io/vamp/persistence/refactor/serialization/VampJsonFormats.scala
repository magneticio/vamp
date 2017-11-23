package io.vamp.persistence.refactor.serialization

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.vamp.common._
import io.vamp.model.artifact._
import io.vamp.persistence.DeploymentServiceStatus
import spray.json._
/**
 * Created by mihai on 11/10/17.
 */
trait VampJsonFormats extends DefaultJsonProtocol with VampJsonDecoders with VampJsonEncoders {

  implicit val environmentVariableSerilizationSpecifier: SerializationSpecifier[EnvironmentVariable] =
    SerializationSpecifier[EnvironmentVariable](environmentVariableEncoder, environmentVariableDecoder, "envVar", (e ⇒ Id[EnvironmentVariable](e.name)))

  implicit val gatewaySerilizationSpecifier: SerializationSpecifier[Gateway] =
    SerializationSpecifier[Gateway](gatewayEncoder, gatewayDecoder, "gateway", (e ⇒ Id[Gateway](e.name)))

  implicit val workflowSerilizationSpecifier: SerializationSpecifier[Workflow] =
    SerializationSpecifier[Workflow](workflowEncoder, workflowDecoder, "workflow", (e ⇒ Id[Workflow](e.name)))

  implicit val blueprintSerilizationSpecifier: SerializationSpecifier[Blueprint] =
    SerializationSpecifier[Blueprint](blueprintEncoder, blueprintDecoder, "blueprint", (e ⇒ Id[Blueprint](e.name)))

  implicit val deploymentSerilizationSpecifier: SerializationSpecifier[Deployment] =
    SerializationSpecifier[Deployment](deploymentEncoder, deploymentDecoder, "deployment", (e ⇒ Id[Deployment](e.name)))

  implicit val breedSerilizationSpecifier: SerializationSpecifier[Breed] =
    SerializationSpecifier[Breed](breedEncoder, breedDecoder, "breed", (e ⇒ Id[Breed](e.name)))

  implicit val scaleSerializationSpecifier: SerializationSpecifier[Scale] =
    SerializationSpecifier[Scale](scaleEncoder, scaleDecoder, "scale", (e => Id[Scale](e.name)))

  implicit val escalationSerializationSpecifier: SerializationSpecifier[Escalation] =
    SerializationSpecifier[Escalation](escalationEncoder, escalationDecoder, "escalation", (e => Id[Escalation](e.name)))

  implicit val routeSerializationSpecifier: SerializationSpecifier[Route] =
    SerializationSpecifier[Route](routeEncoder, routeDecoder, "route", (e => Id[Route](e.name)))

  implicit val conditionSerializationSpecifier: SerializationSpecifier[Condition] =
    SerializationSpecifier[Condition](conditionEncoder, conditionDecoder, "condition", (e => Id[Condition](e.name)))

  implicit val slaSerializationSpecifier: SerializationSpecifier[Sla] =
    SerializationSpecifier[Sla](slaEncoder, slaDecoder, "sla", (e => Id[Sla](e.name)))

  implicit val rewriteSerializationSpecifier: SerializationSpecifier[Rewrite] =
    SerializationSpecifier[Rewrite](rewriteEncoder, rewriteDecoder, "rewrite", (e => Id[Rewrite](e.name)))

  implicit val templateSerializationSpecifier: SerializationSpecifier[Template] =
    SerializationSpecifier[Template](templateEncoder, templateDecoder, "template", (e => Id[Template](e.name)))

  def marshall[T: SerializationSpecifier](obj: T): String = {
    val specifier = implicitly[SerializationSpecifier[T]]
    specifier.encoder(obj).noSpaces
  }

  def marshallList[T: SerializationSpecifier](list: List[T]): String = {
    val specifier = implicitly[SerializationSpecifier[T]]
    implicit val encoder: Encoder[T] = specifier.encoder
    list.asJson.noSpaces
  }

  def interpretJson[T](js: Json, sSpecifier: SerializationSpecifier[T]): T = {
    sSpecifier.decoder.decodeJson(js) match {
      case Right(r) ⇒ r
      case _        ⇒ throw ObjectFormatException(objectAsString = js.noSpaces, `type` = s"${sSpecifier.typeName}")
    }
  }

  def unMarshallList[T: SerializationSpecifier](objListAsString: String): List[T] = {
    val specifier = implicitly[SerializationSpecifier[T]]
    parse(objListAsString) match {
      case Right(r) if (r.isArray) ⇒ r.asArray.map(_.toList).getOrElse(Nil).map(interpretJson(_, specifier))
      case _                       ⇒ throw ObjectFormatException(objectAsString = objListAsString, `type` = s"List of ${specifier.typeName}")
    }
  }

}

