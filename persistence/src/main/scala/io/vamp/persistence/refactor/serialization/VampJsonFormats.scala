package io.vamp.persistence.refactor.serialization

import io.circe._
import io.circe.parser._
import io.circe.syntax._
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
      case Right(r) => r
      case _ => throw ObjectFormatException(objectAsString = js.noSpaces, `type` = s"${sSpecifier.typeName}")
    }
  }

  def unMarshallList[T: SerializationSpecifier](objListAsString: String): List[T] = {
    val specifier = implicitly[SerializationSpecifier[T]]
    parse(objListAsString) match {
      case Right(r) if (r.isArray) => r.asArray.map(_.toList).getOrElse(Nil).map(interpretJson(_, specifier))
      case _ => throw ObjectFormatException(objectAsString = objListAsString, `type` = s"List of ${specifier.typeName}")
    }
  }

}


