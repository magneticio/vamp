package io.vamp.persistence.refactor.serialization

import io.vamp.common._
import io.vamp.model.artifact._
import spray.json._
import io.circe._
import io.circe.generic.semiauto._
import io.vamp.model.reader.Percentage

import scala.util.{Failure, Success, Try}

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
    Try(hc.as[String] map (e => enum.withName(e))) match {
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

  implicit val restrictedIntEncoder: Encoder[RestrictedInt] = Encoder.instance[RestrictedInt] {e => Json.fromInt(e.i)}
  implicit val restrictedDoubleEncoder: Encoder[RestrictedDouble] = Encoder.instance[RestrictedDouble]
    { e => Json.fromDouble(e.d).getOrElse(throw ObjectFormatException(e.d.toString, "Double"))}
  implicit val restrictedBooleanEncoder: Encoder[RestrictedBoolean] = Encoder.instance[RestrictedBoolean] { e => Json.fromBoolean(e.b)}
  implicit val restrictedStringEncoder: Encoder[RestrictedString] = Encoder.instance[RestrictedString] {e => Json.fromString(e.s)}
  implicit val restrictedMapEncoder: Encoder[RestrictedMap] = Encoder.instance[RestrictedMap]
    { e => Json.fromJsonObject(JsonObject.fromIterable(e.mp.toList.map {
      a => (a._1, restrictedAnyEncoder(a._2))
    }))}

  implicit val restrictedListEncoder: Encoder[RestrictedList] = Encoder.instance[RestrictedList]
    { e => Json.fromValues(e.ls.map { a => restrictedAnyEncoder(a)})}

  implicit val restrictedAnyEncoder: Encoder[RestrictedAny] = Encoder.instance[RestrictedAny] { _ match {
    case e: RestrictedInt => restrictedIntEncoder(e)
    case e: RestrictedDouble => restrictedDoubleEncoder(e)
    case e: RestrictedBoolean => restrictedBooleanEncoder(e)
    case e: RestrictedString => restrictedStringEncoder.apply(e)
    case e: RestrictedMap => restrictedMapEncoder(e)
    case e: RestrictedList => restrictedListEncoder(e)
    }
  }

  implicit val restrictedEncoder: Encoder[RootAnyMap] = Encoder.instance[RootAnyMap] { rmp =>
    Json.fromString(restrictedMapEncoder(RestrictedMap(rmp.rootMap)).noSpaces.replace("\"", "\\\""))
  }





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


  implicit val restrictedIntDecoder: Decoder[RestrictedInt] = Decoder.instance[RestrictedInt] { hc =>
    hc.as[Int].map(iVal => RestrictedInt(iVal))
  }

  implicit val restrictedDoubleDecoder: Decoder[RestrictedDouble] = Decoder.instance[RestrictedDouble] { hc =>
    hc.as[Double].map(dVal => RestrictedDouble(dVal))
  }

  implicit val restrictedBooleanDecoder: Decoder[RestrictedBoolean] = Decoder.instance[RestrictedBoolean] { hc =>
    hc.as[Boolean].map(bVal => RestrictedBoolean(bVal))
  }

  implicit val restrictedStringDecoder: Decoder[RestrictedString] = Decoder.instance[RestrictedString] { hc =>
    hc.as[String].map(sVal => RestrictedString(sVal))
  }


  def eitherToTry[A, E <: Throwable](either: Either[E,  A]): Try[A] = either match {
    case Left(e) => Failure(e)
    case Right(r) => Success(r)
  }

  implicit val restrictedListDecoder: Decoder[RestrictedList] = Decoder.instance[RestrictedList] { hc => {
    Try(hc.values.getOrElse(Nil).map{ value =>
      eitherToTry(restrictedAnyDecoder.decodeJson(value)).get
    }) match {
      case Failure(e: DecodingFailure) => Left[DecodingFailure, RestrictedList](e)
      case Failure(e: Throwable) => Left[DecodingFailure, RestrictedList](DecodingFailure(e.getMessage, hc.history))
      case Success(e) => Right[DecodingFailure, RestrictedList](RestrictedList(e.toList))
    }
  }}

  implicit val restrictedMapDecoder: Decoder[RestrictedMap] = Decoder.instance[RestrictedMap] { hc => {
      hc.value match {
        case x if(x.isArray) => Left[DecodingFailure, RestrictedMap](DecodingFailure(s"Cannot interpret iterable-sequence ${hc.toString} as map", hc.history))
        case _ => Try(hc.fields.map(_.toList).getOrElse(Nil).map{ field =>
          field -> eitherToTry(restrictedAnyDecoder.tryDecode(hc.downField(field))).get
        }) match {
          case Failure(e: DecodingFailure) => Left[DecodingFailure, RestrictedMap](e)
          case Failure(e: Throwable) => Left[DecodingFailure, RestrictedMap](DecodingFailure(e.getMessage, hc.history))
          case Success(e) => Right[DecodingFailure, RestrictedMap](RestrictedMap(e.toMap))
        }
      }

  }}

  implicit val restrictedRootMap: Decoder[RootAnyMap] = Decoder.instance[RootAnyMap] { hc => {
    import io.circe.parser._
    (for {
      asSring <- eitherToTry(hc.as[String])
      removedEscapedQuotes = asSring.replace("\\\"", "\"")
      parsedJson <- eitherToTry(parse(removedEscapedQuotes))
      decoded <- eitherToTry(restrictedMapDecoder.decodeJson(parsedJson))
    } yield decoded) match {
      case Failure(e: DecodingFailure) => Left[DecodingFailure, RootAnyMap](e)
      case Failure(e: Throwable) => Left[DecodingFailure, RootAnyMap](DecodingFailure(e.getMessage, hc.history))
      case Success(e) => Right[DecodingFailure, RootAnyMap](RootAnyMap(e.mp))
    }
  }}


  private def tryDecoderOrElse(a: Decoder[_<:RestrictedAny], orElse: Option[() => Decoder.Result[RestrictedAny]])(implicit hc: HCursor): Decoder.Result[RestrictedAny] = {
    (a.tryDecode(hc), orElse) match {
      case (Left(_), Some(f)) => f()
      case (response, _) => response
    }
  }

  implicit val restrictedAnyDecoder: Decoder[RestrictedAny] = Decoder.instance[RestrictedAny] { hc =>
    implicit val hCursor: HCursor = hc
    // In cascade, attempt to deserialize this with each available decoder
    tryDecoderOrElse(restrictedIntDecoder, Some(() => tryDecoderOrElse(
        restrictedDoubleDecoder, Some(() => tryDecoderOrElse(
            restrictedStringDecoder,Some( () => tryDecoderOrElse(
              restrictedBooleanDecoder, Some(() => tryDecoderOrElse(restrictedMapDecoder, Some( () => tryDecoderOrElse(
                restrictedListDecoder, None
    )))))))))))
  }

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
