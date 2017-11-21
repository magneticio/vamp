package io.vamp.persistence.refactor.serialization

import java.time.{Duration, OffsetDateTime, ZoneOffset}

import io.vamp.common._
import io.vamp.model.artifact._
import spray.json._
import io.circe._
import io.circe.generic.semiauto._
import io.vamp.model.artifact.TimeSchedule.RepeatPeriod
import io.vamp.model.reader.{MegaByte, Percentage, Quantity, Time}

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


  implicit val deployableEncoder: Encoder[Deployable] = deriveEncoder[Deployable]

  implicit val timeEncoder: Encoder[Time] = deriveEncoder[Time]

  implicit val healthCheckEncoder: Encoder[HealthCheck] = deriveEncoder[HealthCheck]

  implicit val argumentEncoder: Encoder[Argument] = deriveEncoder[Argument]

  implicit val constantEncoder: Encoder[Constant] = deriveEncoder[Constant]

  implicit val breedReferenceEncoder: Encoder[BreedReference] = deriveEncoder[BreedReference]
  implicit val defaultBreedEncoder: Encoder[DefaultBreed] = deriveEncoder[DefaultBreed]
  implicit val breedEncoder: Encoder[Breed] = deriveEncoder[Breed]

  implicit val restartingPhase: Encoder[Workflow.Status.RestartingPhase.Value] = enumEncoder(Workflow.Status.RestartingPhase)

  implicit val workflowStatusEncoder: Encoder[Workflow.Status] = Encoder.instance[Workflow.Status] { _ match {
    case Workflow.Status.Starting => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("Starting"))))
    case Workflow.Status.Stopping => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("Stopping"))))
    case Workflow.Status.Running => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("Running"))))
    case Workflow.Status.Suspended => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("Suspended"))))
    case Workflow.Status.Suspending => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("Suspending"))))
    case Workflow.Status.Restarting(phase) => Json.fromJsonObject(JsonObject.fromMap(
      Map(
        ("type" -> Json.fromString("Restarting")),
        ("args" -> Json.fromJsonObject(JsonObject.fromMap(
          List(phase.map(ph => ("phase" -> restartingPhase(ph)))).flatten.toMap
        )))
      )
    ))
  }
  }

  implicit val quantityEncoder: Encoder[Quantity] = deriveEncoder[Quantity]
  implicit val megabyteEncoder: Encoder[MegaByte] = deriveEncoder[MegaByte]

  implicit val defaultScaleEncoder: Encoder[DefaultScale] = deriveEncoder[DefaultScale]
  implicit val scaleReferenceEncoder: Encoder[ScaleReference] = deriveEncoder[ScaleReference]
  implicit val scaleEncoder: Encoder[Scale] = deriveEncoder[Scale]

  implicit val instanceEncoder: Encoder[Instance] = deriveEncoder[Instance]
  implicit val healthEncoder: Encoder[Health] = deriveEncoder[Health]

  implicit val periodEncoder: Encoder[java.time.Period] = {
    implicit val period_AuxEncoder: Encoder[Period_AuxForSerialazation] = deriveEncoder[Period_AuxForSerialazation]
    Encoder.instance[java.time.Period] { x =>
      period_AuxEncoder.apply(Period_AuxForSerialazation(years = x.getYears, months = x.getMonths, days = x.getDays))
    }
  }
  implicit val durationEncoder: Encoder[java.time.Duration] = {
    implicit val duration_AuxEncoder: Encoder[Duration_AuxForSerialazation] = deriveEncoder[Duration_AuxForSerialazation]
    Encoder.instance[java.time.Duration] { x =>
      duration_AuxEncoder.apply(Duration_AuxForSerialazation(seconds = x.getSeconds, nanos = x.getNano))
    }
  }
  implicit val repeatPeriodEncoder: Encoder[RepeatPeriod] = deriveEncoder[RepeatPeriod]
  implicit val repeatEncoder: Encoder[TimeSchedule.Repeat] = Encoder.instance[TimeSchedule.Repeat] { _ match {
    case TimeSchedule.RepeatForever => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("RepeatForever"))))
    case TimeSchedule.RepeatCount(count) => Json.fromJsonObject(JsonObject.fromMap(
      Map(
        ("type" -> Json.fromString("RepeatCount")),
        ("args" -> Json.fromJsonObject(JsonObject.fromMap(
          Map("count" -> Json.fromInt(count))
        )))
      )
    ))
  }}

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = {
    implicit val zoneOffsetDateTimeEncoder: Encoder[ZoneOffset] = Encoder.instance[ZoneOffset] { x =>Json.fromString(x.getId)}
    implicit val offsetDateTime_AuxForSerializationEncoder: Encoder[OffsetDateTime_AuzForSerialization] = deriveEncoder[OffsetDateTime_AuzForSerialization]
    Encoder.instance[java.time.OffsetDateTime] { x =>
      offsetDateTime_AuxForSerializationEncoder.apply(
        OffsetDateTime_AuzForSerialization(x.getYear, x.getMonth.getValue, x.getDayOfMonth, x.getHour, x.getMinute, x.getSecond, x.getNano, x.getOffset)
      )
    }
  }

  implicit val timeScheduleEncoder: Encoder[TimeSchedule] = deriveEncoder[TimeSchedule]
  implicit val eventScheduleEncoder: Encoder[EventSchedule] = deriveEncoder[EventSchedule]

  implicit val scheduleEncoder: Encoder[Schedule] = Encoder.instance[Schedule] { _ match {
    case DaemonSchedule => Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("DaemonSchedule"))))
    case e:TimeSchedule => Json.fromJsonObject(JsonObject.fromMap(
      Map(
        ("type" -> Json.fromString("TimeSchedule")),
        ("args" -> timeScheduleEncoder.apply(e))
      )
    ))
    case e:EventSchedule => Json.fromJsonObject(JsonObject.fromMap(
      Map(
        ("type" -> Json.fromString("EventSchedule")),
        ("args" -> eventScheduleEncoder.apply(e))
      )
    ))
  }}

  implicit val workflowEncoder: Encoder[Workflow] = deriveEncoder[Workflow]



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

  implicit val deployableDecoder: Decoder[Deployable] = deriveDecoder[Deployable]

  implicit val timeDecoder: Decoder[Time] = deriveDecoder[Time]

  implicit val healthCheckDecoder: Decoder[HealthCheck] = deriveDecoder[HealthCheck]

  implicit val argumentDecoder: Decoder[Argument] = deriveDecoder[Argument]

  implicit val constantDecoder: Decoder[Constant] = deriveDecoder[Constant]

  implicit val breedReferenceDecoder: Decoder[BreedReference] = deriveDecoder[BreedReference]
  implicit val defaultBreedDecoder: Decoder[DefaultBreed] = deriveDecoder[DefaultBreed]

  implicit val breedDecoder: Decoder[Breed] = deriveDecoder[Breed]

  implicit val restartingPhaseDecoder: Decoder[Workflow.Status.RestartingPhase.Value] = enumDecoder(Workflow.Status.RestartingPhase)

  implicit val workflowStatusDecoder: Decoder[Workflow.Status] = Decoder.instance[Workflow.Status] { hc => {
    hc.downField("type").as[String] match {
      case Right(v) if(v == "Starting") => Right(Workflow.Status.Starting)
      case Right(v) if(v == "Stopping") => Right(Workflow.Status.Stopping)
      case Right(v) if(v == "Running") => Right(Workflow.Status.Running)
      case Right(v) if(v == "Suspended") => Right(Workflow.Status.Suspended)
      case Right(v) if(v == "Suspending") => Right(Workflow.Status.Suspending)
      case Right(v) if(v == "Restarting") => {
        hc.downField("args").downField("phase").as[Workflow.Status.RestartingPhase.Value] match {
          case Right(someVal) => Right(Workflow.Status.Restarting(Some(someVal)))
          case _ => Right(Workflow.Status.Restarting(None))
        }
      }
      case _ => Left(DecodingFailure("Unable to extract as Starting, Stopping, Running, Suspended, Suspending, Restarting", ops = hc.history))
    }
  }}

  implicit val quantityDecoder: Decoder[Quantity] = deriveDecoder[Quantity]
  implicit val megabyteDecoder: Decoder[MegaByte] = deriveDecoder[MegaByte]

  implicit val defaultScaleDecoder: Decoder[DefaultScale] = deriveDecoder[DefaultScale]
  implicit val scaleReferenceDecoder: Decoder[ScaleReference] = deriveDecoder[ScaleReference]
  implicit val scaleDecoder: Decoder[Scale] = deriveDecoder[Scale]

  implicit val instanceDecoder: Decoder[Instance] = deriveDecoder[Instance]

  implicit val healthDecoder: Decoder[Health] = deriveDecoder[Health]


  implicit val periodDecoder: Decoder[java.time.Period] = {
    implicit val period_AuxDecoder: Decoder[Period_AuxForSerialazation] = deriveDecoder[Period_AuxForSerialazation]
    Decoder.instance[java.time.Period] { hc =>
      period_AuxDecoder(hc) match {
        case Left(e) => Left(e)
        case Right(r) => Right(java.time.Period.of(r.years, r.months, r.days))
      }
    }
  }
  implicit val durationDecoder: Decoder[java.time.Duration] = {
    implicit val duration_AuxDecoder: Decoder[Duration_AuxForSerialazation] = deriveDecoder[Duration_AuxForSerialazation]
    Decoder.instance[java.time.Duration] { hc =>
      duration_AuxDecoder(hc) match {
        case Left(e) => Left(e)
        case Right(r) => Right(Duration.ofSeconds(r.seconds, r.nanos))
      }
    }
  }
  implicit val repeatPeriodDecoder: Decoder[RepeatPeriod] = deriveDecoder[RepeatPeriod]

  implicit val repeatDecoder: Decoder[TimeSchedule.Repeat] = Decoder.instance[TimeSchedule.Repeat] { hc => {
    hc.downField("type").as[String] match {
      case Right(v) if(v == "RepeatForever") => Right(TimeSchedule.RepeatForever)
      case Right(v) if(v == "RepeatCount") => {
        hc.downField("args").downField("count").as[Int] match {
          case Right(someVal) => Right(TimeSchedule.RepeatCount(someVal))
          case _ => Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
        }
      }
      case _ => Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
    }
  }}

  implicit val offsetDateTimDecoder: Decoder[java.time.OffsetDateTime] = {
    implicit val zoneOffsetDecoder: Decoder[ZoneOffset] = Decoder.instance[ZoneOffset] {
      hc => hc.as[String].map(x => ZoneOffset.of(x))
    }
    implicit val offsetDateTimeAux_Decoder: Decoder[OffsetDateTime_AuzForSerialization] = deriveDecoder[OffsetDateTime_AuzForSerialization]
    Decoder.instance[java.time.OffsetDateTime] { hc =>
      offsetDateTimeAux_Decoder(hc) match {
        case Left(e) => Left(e)
        case Right(r) => Right(java.time.OffsetDateTime.of(r.year, r.month, r.dayOfMonth, r.hour, r.minute, r.second, r.nanoOfSecond, r.offset))
      }
    }
  }

  implicit val timeScheduleDecoder: Decoder[TimeSchedule] = deriveDecoder[TimeSchedule]
  implicit val eventScheduleDecoder : Decoder[EventSchedule] = deriveDecoder[EventSchedule]

  implicit val scheduleDecoder: Decoder[Schedule] = Decoder.instance[Schedule] { hc => {
    hc.downField("type").as[String] match {
      case Right(v) if(v == "DaemonSchedule") => Right(DaemonSchedule)
      case Right(v) if(v == "TimeSchedule") => {hc.downField("args").as[TimeSchedule]}
      case Right(v) if(v == "EventSchedule") => {hc.downField("args").as[EventSchedule]}
      case _ => Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
    }
  }}

  implicit val workflowDecoder: Decoder[Workflow] = deriveDecoder[Workflow]

  // ========================================= Serialization Specifiers ================================================
  implicit val environmentVariableSerilizationSpecifier: SerializationSpecifier[EnvironmentVariable] =
    SerializationSpecifier[EnvironmentVariable](environmentVariableEncoder, environmentVariableDecoder, "envVar", (e ⇒ Id[EnvironmentVariable](e.name)))

  implicit val gatewaySerilizationSpecifier: SerializationSpecifier[Gateway] =
    SerializationSpecifier[Gateway](gatewayEncoder, gatewayDecoder, "gateway", (e ⇒ Id[Gateway](e.name)))

  implicit val workflowSerilizationSpecifier: SerializationSpecifier[Workflow] =
    SerializationSpecifier[Workflow](workflowEncoder, workflowDecoder, "workflow", (e ⇒ Id[Workflow](e.name)))


  private case class Period_AuxForSerialazation(years: Int, months: Int, days: Int)
  private case class Duration_AuxForSerialazation(seconds: Long, nanos: Int)
  private case class OffsetDateTime_AuzForSerialization(year: Int, month: Int, dayOfMonth: Int,
    hour: Int, minute: Int, second: Int, nanoOfSecond: Int, offset: ZoneOffset)
}

case class SerializationSpecifier[T](encoder: Encoder[T], decoder: Decoder[T], typeName: String, idExtractor: T ⇒ Id[T])
