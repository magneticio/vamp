package io.vamp.persistence.refactor.serialization

import java.time.{ Duration, ZoneOffset }

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{ Decoder, DecodingFailure, HCursor }
import io.vamp.common._
import io.vamp.model.artifact.DeploymentService.Status.Intention.StatusIntentionType
import io.vamp.model.artifact.TimeSchedule.RepeatPeriod
import io.vamp.model.artifact.{ Host, _ }

import scala.concurrent.duration.{ FiniteDuration, TimeUnit }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Percentage, Quantity, Time }
import io.vamp.persistence.DeploymentServiceStatus

import scala.util.{ Failure, Success, Try }

/**
 * Created by mihai on 11/21/17.
 */
trait VampJsonDecoders {

  def enumDecoder[E <: Enumeration](enum: E) = Decoder.instance { hc ⇒
    Try(hc.as[String] map (e ⇒ enum.withName(e))) match {
      case Failure(ex)   ⇒ throw DecodingFailure(ex.getMessage, hc.history)
      case Success(eVal) ⇒ eVal
    }
  }

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

  implicit val restrictedIntDecoder: Decoder[RestrictedInt] = Decoder.instance[RestrictedInt] { hc ⇒
    hc.as[Int].map(iVal ⇒ RestrictedInt(iVal))
  }

  implicit val restrictedDoubleDecoder: Decoder[RestrictedDouble] = Decoder.instance[RestrictedDouble] { hc ⇒
    hc.as[Double].map(dVal ⇒ RestrictedDouble(dVal))
  }

  implicit val restrictedBooleanDecoder: Decoder[RestrictedBoolean] = Decoder.instance[RestrictedBoolean] { hc ⇒
    hc.as[Boolean].map(bVal ⇒ RestrictedBoolean(bVal))
  }

  implicit val restrictedStringDecoder: Decoder[RestrictedString] = Decoder.instance[RestrictedString] { hc ⇒
    hc.as[String].map(sVal ⇒ RestrictedString(sVal))
  }

  def eitherToTry[A, E <: Throwable](either: Either[E, A]): Try[A] = either match {
    case Left(e)  ⇒ Failure(e)
    case Right(r) ⇒ Success(r)
  }

  implicit val restrictedListDecoder: Decoder[RestrictedList] = Decoder.instance[RestrictedList] { hc ⇒
    {
      Try(hc.values.getOrElse(Nil).map { value ⇒
        eitherToTry(restrictedAnyDecoder.decodeJson(value)).get
      }) match {
        case Failure(e: DecodingFailure) ⇒ Left[DecodingFailure, RestrictedList](e)
        case Failure(e: Throwable)       ⇒ Left[DecodingFailure, RestrictedList](DecodingFailure(e.getMessage, hc.history))
        case Success(e)                  ⇒ Right[DecodingFailure, RestrictedList](RestrictedList(e.toList))
      }
    }
  }

  implicit val restrictedMapDecoder: Decoder[RestrictedMap] = Decoder.instance[RestrictedMap] { hc ⇒
    {
      hc.value match {
        case x if (x.isArray) ⇒ Left[DecodingFailure, RestrictedMap](DecodingFailure(s"Cannot interpret iterable-sequence ${hc.toString} as map", hc.history))
        case _ ⇒ Try(hc.fields.map(_.toList).getOrElse(Nil).map { field ⇒
          field → eitherToTry(restrictedAnyDecoder.tryDecode(hc.downField(field))).get
        }) match {
          case Failure(e: DecodingFailure) ⇒ Left[DecodingFailure, RestrictedMap](e)
          case Failure(e: Throwable)       ⇒ Left[DecodingFailure, RestrictedMap](DecodingFailure(e.getMessage, hc.history))
          case Success(e)                  ⇒ Right[DecodingFailure, RestrictedMap](RestrictedMap(e.toMap))
        }
      }

    }
  }

  implicit val restrictedRootMap: Decoder[RootAnyMap] = Decoder.instance[RootAnyMap] { hc ⇒
    {
      import io.circe.parser._
      (for {
        asSring ← eitherToTry(hc.as[String])
        removedEscapedQuotes = asSring.replace("\\\"", "\"")
        parsedJson ← eitherToTry(parse(removedEscapedQuotes))
        decoded ← eitherToTry(restrictedMapDecoder.decodeJson(parsedJson))
      } yield decoded) match {
        case Failure(e: DecodingFailure) ⇒ Left[DecodingFailure, RootAnyMap](e)
        case Failure(e: Throwable)       ⇒ Left[DecodingFailure, RootAnyMap](DecodingFailure(e.getMessage, hc.history))
        case Success(e)                  ⇒ Right[DecodingFailure, RootAnyMap](RootAnyMap(e.mp))
      }
    }
  }

  private def tryDecoderOrElse(a: Decoder[_ <: RestrictedAny], orElse: Option[() ⇒ Decoder.Result[RestrictedAny]])(implicit hc: HCursor): Decoder.Result[RestrictedAny] = {
    (a.tryDecode(hc), orElse) match {
      case (Left(_), Some(f)) ⇒ f()
      case (response, _)      ⇒ response
    }
  }

  implicit val restrictedAnyDecoder: Decoder[RestrictedAny] = Decoder.instance[RestrictedAny] { hc ⇒
    implicit val hCursor: HCursor = hc
    // In cascade, attempt to deserialize this with each available decoder
    tryDecoderOrElse(restrictedIntDecoder, Some(() ⇒ tryDecoderOrElse(
      restrictedDoubleDecoder, Some(() ⇒ tryDecoderOrElse(
      restrictedStringDecoder, Some(() ⇒ tryDecoderOrElse(
      restrictedBooleanDecoder, Some(() ⇒ tryDecoderOrElse(restrictedMapDecoder, Some(() ⇒ tryDecoderOrElse(
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

  implicit val workflowStatusDecoder: Decoder[Workflow.Status] = Decoder.instance[Workflow.Status] { hc ⇒
    {
      hc.downField("type").as[String] match {
        case Right(v) if (v == "Starting")   ⇒ Right(Workflow.Status.Starting)
        case Right(v) if (v == "Stopping")   ⇒ Right(Workflow.Status.Stopping)
        case Right(v) if (v == "Running")    ⇒ Right(Workflow.Status.Running)
        case Right(v) if (v == "Suspended")  ⇒ Right(Workflow.Status.Suspended)
        case Right(v) if (v == "Suspending") ⇒ Right(Workflow.Status.Suspending)
        case Right(v) if (v == "Restarting") ⇒ {
          hc.downField("args").downField("phase").as[Workflow.Status.RestartingPhase.Value] match {
            case Right(someVal) ⇒ Right(Workflow.Status.Restarting(Some(someVal)))
            case _              ⇒ Right(Workflow.Status.Restarting(None))
          }
        }
        case _ ⇒ Left(DecodingFailure("Unable to extract as Starting, Stopping, Running, Suspended, Suspending, Restarting", ops = hc.history))
      }
    }
  }

  implicit val quantityDecoder: Decoder[Quantity] = deriveDecoder[Quantity]
  implicit val megabyteDecoder: Decoder[MegaByte] = deriveDecoder[MegaByte]

  implicit val defaultScaleDecoder: Decoder[DefaultScale] = deriveDecoder[DefaultScale]
  implicit val scaleReferenceDecoder: Decoder[ScaleReference] = deriveDecoder[ScaleReference]
  implicit val scaleDecoder: Decoder[Scale] = deriveDecoder[Scale]

  implicit val instanceDecoder: Decoder[Instance] = deriveDecoder[Instance]

  implicit val healthDecoder: Decoder[Health] = deriveDecoder[Health]

  implicit val periodDecoder: Decoder[java.time.Period] = {
    implicit val period_AuxDecoder: Decoder[Period_AuxForSerialazation] = deriveDecoder[Period_AuxForSerialazation]
    Decoder.instance[java.time.Period] { hc ⇒
      period_AuxDecoder(hc) match {
        case Left(e)  ⇒ Left(e)
        case Right(r) ⇒ Right(java.time.Period.of(r.years, r.months, r.days))
      }
    }
  }
  implicit val durationDecoder: Decoder[java.time.Duration] = {
    implicit val duration_AuxDecoder: Decoder[Duration_AuxForSerialazation] = deriveDecoder[Duration_AuxForSerialazation]
    Decoder.instance[java.time.Duration] { hc ⇒
      duration_AuxDecoder(hc) match {
        case Left(e)  ⇒ Left(e)
        case Right(r) ⇒ Right(Duration.ofSeconds(r.seconds, r.nanos))
      }
    }
  }
  implicit val repeatPeriodDecoder: Decoder[RepeatPeriod] = deriveDecoder[RepeatPeriod]

  implicit val repeatDecoder: Decoder[TimeSchedule.Repeat] = Decoder.instance[TimeSchedule.Repeat] { hc ⇒
    {
      hc.downField("type").as[String] match {
        case Right(v) if (v == "RepeatForever") ⇒ Right(TimeSchedule.RepeatForever)
        case Right(v) if (v == "RepeatCount") ⇒ {
          hc.downField("args").downField("count").as[Int] match {
            case Right(someVal) ⇒ Right(TimeSchedule.RepeatCount(someVal))
            case _              ⇒ Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
          }
        }
        case _ ⇒ Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
      }
    }
  }

  implicit val offsetDateTimDecoder: Decoder[java.time.OffsetDateTime] = {
    implicit val zoneOffsetDecoder: Decoder[ZoneOffset] = Decoder.instance[ZoneOffset] {
      hc ⇒ hc.as[String].map(x ⇒ ZoneOffset.of(x))
    }
    implicit val offsetDateTimeAux_Decoder: Decoder[OffsetDateTime_AuzForSerialization] = deriveDecoder[OffsetDateTime_AuzForSerialization]
    Decoder.instance[java.time.OffsetDateTime] { hc ⇒
      offsetDateTimeAux_Decoder(hc) match {
        case Left(e)  ⇒ Left(e)
        case Right(r) ⇒ Right(java.time.OffsetDateTime.of(r.year, r.month, r.dayOfMonth, r.hour, r.minute, r.second, r.nanoOfSecond, r.offset))
      }
    }
  }

  implicit val timeScheduleDecoder: Decoder[TimeSchedule] = deriveDecoder[TimeSchedule]
  implicit val eventScheduleDecoder: Decoder[EventSchedule] = deriveDecoder[EventSchedule]

  implicit val scheduleDecoder: Decoder[Schedule] = Decoder.instance[Schedule] { hc ⇒
    {
      hc.downField("type").as[String] match {
        case Right(v) if (v == "DaemonSchedule") ⇒ Right(DaemonSchedule)
        case Right(v) if (v == "TimeSchedule")   ⇒ { hc.downField("args").as[TimeSchedule] }
        case Right(v) if (v == "EventSchedule")  ⇒ { hc.downField("args").as[EventSchedule] }
        case _                                   ⇒ Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
      }
    }
  }

  implicit val workflowDecoder: Decoder[Workflow] = deriveDecoder[Workflow]

  implicit val statusIntentionTypeDecoder: Decoder[StatusIntentionType] = enumDecoder(DeploymentService.Status.Intention)
  implicit val deploymentServicePhaseInitiatedDecoder: Decoder[DeploymentService.Status.Phase.Initiated] = deriveDecoder[DeploymentService.Status.Phase.Initiated]
  implicit val deploymentServicePhaseUpdatingDecoder: Decoder[DeploymentService.Status.Phase.Updating] = deriveDecoder[DeploymentService.Status.Phase.Updating]
  implicit val deploymentServicePhaseDoneDecoder: Decoder[DeploymentService.Status.Phase.Done] = deriveDecoder[DeploymentService.Status.Phase.Done]
  implicit val deploymentServicePhaseFailedDecoder: Decoder[DeploymentService.Status.Phase.Failed] = deriveDecoder[DeploymentService.Status.Phase.Failed]

  implicit val deploymentServicePhaseDecoder: Decoder[DeploymentService.Status.Phase] = Decoder.instance[DeploymentService.Status.Phase] { hc ⇒
    {
      hc.downField("type").as[String] match {
        case Right(v) if (v == "Initiated") ⇒ { hc.downField("args").as[DeploymentService.Status.Phase.Initiated] }
        case Right(v) if (v == "Updating")  ⇒ { hc.downField("args").as[DeploymentService.Status.Phase.Updating] }
        case Right(v) if (v == "Done")      ⇒ { hc.downField("args").as[DeploymentService.Status.Phase.Done] }
        case Right(v) if (v == "Failed")    ⇒ { hc.downField("args").as[DeploymentService.Status.Phase.Failed] }
        case _                              ⇒ Left(DecodingFailure(s"Unable ${hc.toString} to extract as RepeatForever, RepeatCount", ops = hc.history))
      }
    }
  }

  implicit val deploymentServiceStatusDecoder: Decoder[DeploymentService.Status] = deriveDecoder[DeploymentService.Status]

  implicit val deploymenServiceDecoder: Decoder[DeploymentService] = deriveDecoder[DeploymentService]
  implicit val serviceDecoder: Decoder[Service] = deriveDecoder[Service]
  implicit val abstractServiceDecoder: Decoder[AbstractService] = deriveDecoder[AbstractService]

  implicit val timeUnitDecoder: Decoder[TimeUnit] = Decoder.instance { hc ⇒
    hc.as[String] match {
      case Right(v) if (v == "DAYS")         ⇒ Right(scala.concurrent.duration.DAYS)
      case Right(v) if (v == "HOURS")        ⇒ Right(scala.concurrent.duration.HOURS)
      case Right(v) if (v == "MICROSECONDS") ⇒ Right(scala.concurrent.duration.MICROSECONDS)
      case Right(v) if (v == "MILLISECONDS") ⇒ Right(scala.concurrent.duration.MILLISECONDS)
      case Right(v) if (v == "MINUTES")      ⇒ Right(scala.concurrent.duration.MINUTES)
      case Right(v) if (v == "NANOSECONDS")  ⇒ Right(scala.concurrent.duration.NANOSECONDS)
      case Right(v) if (v == "SECONDS")      ⇒ Right(scala.concurrent.duration.SECONDS)
      case _                                 ⇒ Left(DecodingFailure(s"Unable ${hc.toString} to extract as TimeUnit", ops = hc.history))
    }
  }

  implicit val timeUnit_AuxForSerializationDecoder: Decoder[TimeUnit_AuxForSerialization] = deriveDecoder[TimeUnit_AuxForSerialization]
  implicit val finiteDurationDecoder: Decoder[FiniteDuration] = Decoder.instance { hc ⇒
    hc.as[TimeUnit_AuxForSerialization] match {
      case Right(v) ⇒ Right(new FiniteDuration(v.length, v.unit))
      case Left(e)  ⇒ Left(e)
    }
  }

  implicit val scaleInstancesEscalationDecoder: Decoder[ScaleInstancesEscalation] = deriveDecoder[ScaleInstancesEscalation]
  implicit val scaleMemoryEscalationDecoder: Decoder[ScaleMemoryEscalation] = deriveDecoder[ScaleMemoryEscalation]
  implicit val scaleCPUEscalationDecoder: Decoder[ScaleCpuEscalation] = deriveDecoder[ScaleCpuEscalation]
  implicit val toAllEscalationDecoder: Decoder[ToAllEscalation] = deriveDecoder[ToAllEscalation]
  implicit val toOneEscalationDecoder: Decoder[ToOneEscalation] = deriveDecoder[ToOneEscalation]
  implicit val genericEscalationDecoder: Decoder[GenericEscalation] = deriveDecoder[GenericEscalation]
  implicit val escalationReferenceDecoder: Decoder[EscalationReference] = deriveDecoder[EscalationReference]
  implicit val groupEscalationDecoder: Decoder[GroupEscalation] = deriveDecoder[GroupEscalation]
  implicit val scaleEscalationDecoder: Decoder[ScaleEscalation] = deriveDecoder[ScaleEscalation]
  implicit val escalationDecoder: Decoder[Escalation] = deriveDecoder[Escalation]

  implicit val responseTimeSlidingWindowSlaDecoder: Decoder[ResponseTimeSlidingWindowSla] = deriveDecoder[ResponseTimeSlidingWindowSla]

  implicit val slidingWindowSlaDecoder: Decoder[SlidingWindowSla] = deriveDecoder[SlidingWindowSla]

  implicit val escalationOnlySlaDecoder: Decoder[EscalationOnlySla] = deriveDecoder[EscalationOnlySla]
  implicit val genericSlaDecoder: Decoder[GenericSla] = deriveDecoder[GenericSla]
  implicit val slaReferenceDecoder: Decoder[SlaReference] = deriveDecoder[SlaReference]
  implicit val slaDecoder: Decoder[Sla] = deriveDecoder[Sla]
  implicit val clusterDecoder: Decoder[Cluster] = deriveDecoder[Cluster]

  implicit val deloymentClusterDecoder: Decoder[DeploymentCluster] = deriveDecoder[DeploymentCluster]
  implicit val abstractClusterDecoder: Decoder[AbstractCluster] = deriveDecoder[AbstractCluster]

  implicit val defaultBlueprintDecoder: Decoder[DefaultBlueprint] = deriveDecoder[DefaultBlueprint]

  implicit val hostDecoder: Decoder[Host] = deriveDecoder[Host]

  implicit val deploymentDecoder: Decoder[Deployment] = deriveDecoder[Deployment]

  implicit val abstractBlueprintDecoder: Decoder[AbstractBlueprint] = deriveDecoder[AbstractBlueprint]

  implicit val blueprintReferenceDecoder: Decoder[BlueprintReference] = deriveDecoder[BlueprintReference]

  implicit val blueprintDecoder: Decoder[Blueprint] = deriveDecoder[Blueprint]

  implicit val beploymentServiceStatusDecoder: Decoder[DeploymentServiceStatus] = deriveDecoder[DeploymentServiceStatus]

}
