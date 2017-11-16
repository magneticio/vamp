package io.vamp.persistence.refactor.serialization

import io.vamp.common.Id
import io.vamp.model.artifact._
import spray.json._
import io.circe._
import io.circe.generic.semiauto._
import io.vamp.model.reader.Percentage

import scala.util.{Try, Success, Failure}

/**
 * Created by mihai on 11/10/17.
 */
trait VampJsonFormats extends DefaultJsonProtocol {
  def enumFormat[E <: Enumeration](enum: E) = new RootJsonFormat[E#Value] {
    def write(value: E#Value) = JsString(value.toString)
    def read(value: JsValue): E#Value = value match {
      case JsString(s) =>
        try enum.withName(s)
        catch {
          case e: Throwable => deserializationError(s"Expected any of ${enum.values.mkString(",")}, but got " + s)
        }
      case x => deserializationError(s"Expected any of ${enum.values.mkString(",")}, but got " + x)
    }
  }

  def enumDecoder[E <: Enumeration](enum: E) = Decoder.instance { hc =>
    Try(hc.as[String] map (enum.withName(_))) match {
      case Failure(ex) => throw DecodingFailure(ex.getMessage, hc.history)
      case Success(eVal) => eVal
    }
  }

  def enumEncoder[E <: Enumeration](enum: E) = Encoder.instance[E#Value] { e =>
    Json.fromString(e.toString)
  }


  // ========================================= ENCODERS ================================================================
  implicit val gatewayPathEncoder: Encoder[GatewayPath] = deriveEncoder[GatewayPath]
  implicit val routeReferenceEncoder: Encoder[RouteReference] = deriveEncoder[RouteReference]
  implicit val percentageEncoder: Encoder[Percentage] = deriveEncoder[Percentage]
  implicit val conditionReferenceEncoder: Encoder[ConditionReference] = deriveEncoder[ConditionReference]
  implicit val defaultconditionEncoder: Encoder[DefaultCondition] = deriveEncoder[DefaultCondition]
  implicit val conditionEncoder: Encoder[Condition] = deriveEncoder[Condition]
  implicit val externalrouteTargeteEncoder: Encoder[InternalRouteTarget] = deriveEncoder[InternalRouteTarget]
  implicit val inernalrouteTargeteEncoder: Encoder[ExternalRouteTarget] = deriveEncoder[ExternalRouteTarget]
  implicit val routeTargeteEncoder: Encoder[RouteTarget] = deriveEncoder[RouteTarget]

  implicit val rewriteReferenceEncoder: Encoder[RewriteReference] = deriveEncoder[RewriteReference]
  implicit val pathRewriteEncoder: Encoder[PathRewrite] = deriveEncoder[PathRewrite]
  implicit val rewriteEncoder: Encoder[Rewrite] = deriveEncoder[Rewrite]
  implicit val defaultRouteEncoder: Encoder[DefaultRoute] = deriveEncoder[DefaultRoute]
  implicit val routeEncoder: Encoder[Route] = deriveEncoder[Route]

  implicit val portTypeEncoder: Encoder[Port.Type.Type] = enumEncoder(Port.Type)
  implicit val portEncoder: Encoder[Port] = deriveEncoder[Port]
  implicit val gatewayServiceEncoder: Encoder[GatewayService] = deriveEncoder[GatewayService]
  implicit val gatewayStickyValueEncoder: Encoder[Gateway.Sticky.Value] = enumEncoder(Gateway.Sticky)

  implicit val gatewayEncoder: Encoder[Gateway] = deriveEncoder[Gateway]

  implicit val environmentVariableEncoder: Encoder[EnvironmentVariable] = deriveEncoder[EnvironmentVariable]


  // ========================================= DECODERS ================================================================
  implicit val gatewayPathDecoder: Decoder[GatewayPath] = deriveDecoder[GatewayPath]
  implicit val routeReferenceDecoder: Decoder[RouteReference] = deriveDecoder[RouteReference]
  implicit val percentageDecoder: Decoder[Percentage] = deriveDecoder[Percentage]
  implicit val conditionReferenceDecoder: Decoder[ConditionReference] = deriveDecoder[ConditionReference]
  implicit val defaultconditionDecoder: Decoder[DefaultCondition] = deriveDecoder[DefaultCondition]
  implicit val conditionDecoder: Decoder[Condition] = deriveDecoder[Condition]
  implicit val externalrouteTargeteDecoder: Decoder[InternalRouteTarget] = deriveDecoder[InternalRouteTarget]
  implicit val inernalrouteTargeteDecoder: Decoder[ExternalRouteTarget] = deriveDecoder[ExternalRouteTarget]
  implicit val routeTargeteDecoder: Decoder[RouteTarget] = deriveDecoder[RouteTarget]

  implicit val rewriteReferenceDecoder: Decoder[RewriteReference] = deriveDecoder[RewriteReference]
  implicit val pathRewriteDecoder: Decoder[PathRewrite] = deriveDecoder[PathRewrite]
  implicit val rewriteDecoder: Decoder[Rewrite] = deriveDecoder[Rewrite]
  implicit val defaultRouteDecoder: Decoder[DefaultRoute] = deriveDecoder[DefaultRoute]
  implicit val routeDecoder: Decoder[Route] = deriveDecoder[Route]

  implicit val portTypeDecoder: Decoder[Port.Type.Type] = enumDecoder(Port.Type)
  implicit val portDecoder: Decoder[Port] = deriveDecoder[Port]
  implicit val gatewayServiceDecoder: Decoder[GatewayService] = deriveDecoder[GatewayService]
  implicit val gatewayStickyValueDecoder: Decoder[Gateway.Sticky.Value] = enumDecoder(Gateway.Sticky)

  implicit val gatewayDecoder: Decoder[Gateway] = deriveDecoder[Gateway]

  implicit val environmentVariableDecoder: Decoder[EnvironmentVariable] = deriveDecoder[EnvironmentVariable]


  // ========================================= Serialization Specifiers ================================================
  implicit val environmentVariableSerilizationSpecifier: SerializationSpecifier[EnvironmentVariable] =
    SerializationSpecifier[EnvironmentVariable](environmentVariableEncoder, environmentVariableDecoder, "envVar", (e ⇒ Id[EnvironmentVariable](e.name)))

  implicit val gatewaySerilizationSpecifier: SerializationSpecifier[Gateway] =
    SerializationSpecifier[Gateway](gatewayEncoder, gatewayDecoder, "gateway", (e ⇒ Id[Gateway](e.name)))



/*
  implicit val gatewaySerilizationSpecifier: SerializationSpecifier[Gateway] = {
    implicit val portTypeFormat = enumFormat(Port.Type)
    implicit val portFomat: RootJsonFormat[Port] = jsonFormat5(Port.apply)
    implicit val gatewayServiceFormat: RootJsonFormat[GatewayService] = jsonFormat2(GatewayService)
    implicit val gatewayStockyTypeFormat = enumFormat(Gateway.Sticky)

    SerializationSpecifier[Gateway](jsonFormat7(Gateway.apply), "gateway", (e ⇒ Id[Gateway](e.name)))
  }
*/

}

case class SerializationSpecifier[T](encoder: Encoder[T], decoder: Decoder[T], typeName: String, idExtractor: T ⇒ Id[T])
