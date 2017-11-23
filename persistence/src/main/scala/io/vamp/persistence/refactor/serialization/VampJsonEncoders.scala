package io.vamp.persistence.refactor.serialization

import java.time.{ OffsetDateTime, ZoneOffset }

import io.circe._
import io.circe.generic.semiauto.deriveEncoder
import io.vamp.common._
import io.vamp.model.artifact.DeploymentService.Status.Intention.StatusIntentionType
import io.vamp.model.artifact.TimeSchedule.RepeatPeriod
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Percentage, Quantity, Time }

import scala.concurrent.duration.{ FiniteDuration, TimeUnit }

/**
 * Created by mihai on 11/21/17.
 */
trait VampJsonEncoders {

  def enumEncoder[E <: Enumeration](enum: E) = Encoder.instance[E#Value] { e ⇒
    Json.fromString(e.toString)
  }

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

  implicit val restrictedIntEncoder: Encoder[RestrictedInt] = Encoder.instance[RestrictedInt] { e ⇒ Json.fromInt(e.i) }
  implicit val restrictedDoubleEncoder: Encoder[RestrictedDouble] = Encoder.instance[RestrictedDouble] { e ⇒ Json.fromDouble(e.d).getOrElse(throw ObjectFormatException(e.d.toString, "Double")) }
  implicit val restrictedBooleanEncoder: Encoder[RestrictedBoolean] = Encoder.instance[RestrictedBoolean] { e ⇒ Json.fromBoolean(e.b) }
  implicit val restrictedStringEncoder: Encoder[RestrictedString] = Encoder.instance[RestrictedString] { e ⇒ Json.fromString(e.s) }
  implicit val restrictedMapEncoder: Encoder[RestrictedMap] = Encoder.instance[RestrictedMap] { e ⇒
    Json.fromJsonObject(JsonObject.fromIterable(e.mp.toList.map {
      a ⇒ (a._1, restrictedAnyEncoder(a._2))
    }))
  }

  implicit val restrictedListEncoder: Encoder[RestrictedList] = Encoder.instance[RestrictedList] { e ⇒ Json.fromValues(e.ls.map { a ⇒ restrictedAnyEncoder(a) }) }

  implicit val restrictedAnyEncoder: Encoder[RestrictedAny] = Encoder.instance[RestrictedAny] {
    _ match {
      case e: RestrictedInt     ⇒ restrictedIntEncoder(e)
      case e: RestrictedDouble  ⇒ restrictedDoubleEncoder(e)
      case e: RestrictedBoolean ⇒ restrictedBooleanEncoder(e)
      case e: RestrictedString  ⇒ restrictedStringEncoder.apply(e)
      case e: RestrictedMap     ⇒ restrictedMapEncoder(e)
      case e: RestrictedList    ⇒ restrictedListEncoder(e)
    }
  }

  implicit val restrictedEncoder: Encoder[RootAnyMap] = Encoder.instance[RootAnyMap] { rmp ⇒
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

  implicit val workflowStatusEncoder: Encoder[Workflow.Status] = Encoder.instance[Workflow.Status] {
    _ match {
      case Workflow.Status.Starting   ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("Starting"))))
      case Workflow.Status.Stopping   ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("Stopping"))))
      case Workflow.Status.Running    ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("Running"))))
      case Workflow.Status.Suspended  ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("Suspended"))))
      case Workflow.Status.Suspending ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("Suspending"))))
      case Workflow.Status.Restarting(phase) ⇒ Json.fromJsonObject(JsonObject.fromMap(
        Map(
          ("type" → Json.fromString("Restarting")),
          ("args" → Json.fromJsonObject(JsonObject.fromMap(
            List(phase.map(ph ⇒ ("phase" → restartingPhase(ph)))).flatten.toMap
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
    Encoder.instance[java.time.Period] { x ⇒
      period_AuxEncoder.apply(Period_AuxForSerialazation(years = x.getYears, months = x.getMonths, days = x.getDays))
    }
  }
  implicit val durationEncoder: Encoder[java.time.Duration] = {
    implicit val duration_AuxEncoder: Encoder[Duration_AuxForSerialazation] = deriveEncoder[Duration_AuxForSerialazation]
    Encoder.instance[java.time.Duration] { x ⇒
      duration_AuxEncoder.apply(Duration_AuxForSerialazation(seconds = x.getSeconds, nanos = x.getNano))
    }
  }
  implicit val repeatPeriodEncoder: Encoder[RepeatPeriod] = deriveEncoder[RepeatPeriod]
  implicit val repeatEncoder: Encoder[TimeSchedule.Repeat] = Encoder.instance[TimeSchedule.Repeat] {
    _ match {
      case TimeSchedule.RepeatForever ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("RepeatForever"))))
      case TimeSchedule.RepeatCount(count) ⇒ Json.fromJsonObject(JsonObject.fromMap(
        Map(
          ("type" → Json.fromString("RepeatCount")),
          ("args" → Json.fromJsonObject(JsonObject.fromMap(
            Map("count" → Json.fromInt(count))
          )))
        )
      ))
    }
  }

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = {
    implicit val zoneOffsetDateTimeEncoder: Encoder[ZoneOffset] = Encoder.instance[ZoneOffset] { x ⇒ Json.fromString(x.getId) }
    implicit val offsetDateTime_AuxForSerializationEncoder: Encoder[OffsetDateTime_AuzForSerialization] = deriveEncoder[OffsetDateTime_AuzForSerialization]
    Encoder.instance[java.time.OffsetDateTime] { x ⇒
      offsetDateTime_AuxForSerializationEncoder.apply(
        OffsetDateTime_AuzForSerialization(x.getYear, x.getMonth.getValue, x.getDayOfMonth, x.getHour, x.getMinute, x.getSecond, x.getNano, x.getOffset)
      )
    }
  }

  implicit val timeScheduleEncoder: Encoder[TimeSchedule] = deriveEncoder[TimeSchedule]
  implicit val eventScheduleEncoder: Encoder[EventSchedule] = deriveEncoder[EventSchedule]

  implicit val scheduleEncoder: Encoder[Schedule] = Encoder.instance[Schedule] {
    _ match {
      case DaemonSchedule ⇒ Json.fromJsonObject(JsonObject.fromMap(Map("type" → Json.fromString("DaemonSchedule"))))
      case e: TimeSchedule ⇒ Json.fromJsonObject(JsonObject.fromMap(
        Map(
          ("type" → Json.fromString("TimeSchedule")),
          ("args" → timeScheduleEncoder.apply(e))
        )
      ))
      case e: EventSchedule ⇒ Json.fromJsonObject(JsonObject.fromMap(
        Map(
          ("type" → Json.fromString("EventSchedule")),
          ("args" → eventScheduleEncoder.apply(e))
        )
      ))
    }
  }

  implicit val workflowEncoder: Encoder[Workflow] = deriveEncoder[Workflow]

  implicit val statusIntentionTypeEncoder: Encoder[StatusIntentionType] = enumEncoder(DeploymentService.Status.Intention)
  implicit val deploymentServicePhaseInitiatedEncoder: Encoder[DeploymentService.Status.Phase.Initiated] = deriveEncoder[DeploymentService.Status.Phase.Initiated]
  implicit val deploymentServicePhaseUpdatingEncoder: Encoder[DeploymentService.Status.Phase.Updating] = deriveEncoder[DeploymentService.Status.Phase.Updating]
  implicit val deploymentServicePhaseDoneEncoder: Encoder[DeploymentService.Status.Phase.Done] = deriveEncoder[DeploymentService.Status.Phase.Done]
  implicit val deploymentServicePhaseFailedEncoder: Encoder[DeploymentService.Status.Phase.Failed] = deriveEncoder[DeploymentService.Status.Phase.Failed]

  implicit val deploymentServicePhaseEncoder: Encoder[DeploymentService.Status.Phase] = Encoder.instance[DeploymentService.Status.Phase] {
    _ match {
      case e: DeploymentService.Status.Phase.Initiated ⇒ Json.fromJsonObject(JsonObject.fromMap(Map(
        "type" → Json.fromString("Initiated"), "args" → deploymentServicePhaseInitiatedEncoder(e)
      )))
      case e: DeploymentService.Status.Phase.Updating ⇒ Json.fromJsonObject(JsonObject.fromMap(Map(
        "type" → Json.fromString("Updating"), "args" → deploymentServicePhaseUpdatingEncoder(e)
      )))
      case e: DeploymentService.Status.Phase.Done ⇒ Json.fromJsonObject(JsonObject.fromMap(Map(
        "type" → Json.fromString("Done"), "args" → deploymentServicePhaseDoneEncoder(e)
      )))
      case e: DeploymentService.Status.Phase.Failed ⇒ Json.fromJsonObject(JsonObject.fromMap(Map(
        "type" → Json.fromString("Failed"), "args" → deploymentServicePhaseFailedEncoder(e)
      )))
    }
  }

  implicit val deploymentServiceStatusEncoder: Encoder[DeploymentService.Status] = deriveEncoder[DeploymentService.Status]

  implicit val deploymenServiceEncoder: Encoder[DeploymentService] = deriveEncoder[DeploymentService]
  implicit val serviceEncoder: Encoder[Service] = deriveEncoder[Service]
  implicit val abstractServiceEncoder: Encoder[AbstractService] = deriveEncoder[AbstractService]

  implicit val timeUnitEncoder: Encoder[TimeUnit] = Encoder.instance { tu ⇒
    tu match {
      case scala.concurrent.duration.DAYS         ⇒ Json.fromString("DAYS")
      case scala.concurrent.duration.HOURS        ⇒ Json.fromString("HOURS")
      case scala.concurrent.duration.MICROSECONDS ⇒ Json.fromString("MICROSECONDS")
      case scala.concurrent.duration.MILLISECONDS ⇒ Json.fromString("MILLISECONDS")
      case scala.concurrent.duration.MINUTES      ⇒ Json.fromString("MINUTES")
      case scala.concurrent.duration.NANOSECONDS  ⇒ Json.fromString("NANOSECONDS")
      case scala.concurrent.duration.SECONDS      ⇒ Json.fromString("SECONDS")
    }
  }

  implicit val timeUnit_AuxForSerializationEncoder: Encoder[TimeUnit_AuxForSerialization] = deriveEncoder[TimeUnit_AuxForSerialization]
  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.instance { fd ⇒
    timeUnit_AuxForSerializationEncoder.apply(TimeUnit_AuxForSerialization(fd._1, fd._2))
  }

  implicit val scaleInstancesEscalationEncoder: Encoder[ScaleInstancesEscalation] = deriveEncoder[ScaleInstancesEscalation]
  implicit val scaleMemoryEscalationEncoder: Encoder[ScaleMemoryEscalation] = deriveEncoder[ScaleMemoryEscalation]
  implicit val scaleCPUEscalationEncoder: Encoder[ScaleCpuEscalation] = deriveEncoder[ScaleCpuEscalation]
  implicit val toAllEscalationEncoder: Encoder[ToAllEscalation] = deriveEncoder[ToAllEscalation]
  implicit val toOneEscalationEncoder: Encoder[ToOneEscalation] = deriveEncoder[ToOneEscalation]
  implicit val genericEscalationEncoder: Encoder[GenericEscalation] = deriveEncoder[GenericEscalation]
  implicit val escalationReferenceEncoder: Encoder[EscalationReference] = deriveEncoder[EscalationReference]
  implicit val groupEscalationEncoder: Encoder[GroupEscalation] = deriveEncoder[GroupEscalation]
  implicit val scaleEscalationEncoder: Encoder[ScaleEscalation] = deriveEncoder[ScaleEscalation]
  implicit val escalationEncoder: Encoder[Escalation] = deriveEncoder[Escalation]

  implicit val responseTimeSlidingWindowSlaEncoder: Encoder[ResponseTimeSlidingWindowSla] = deriveEncoder[ResponseTimeSlidingWindowSla]

  implicit val slidingWindowSlaEncoder: Encoder[SlidingWindowSla] = deriveEncoder[SlidingWindowSla]

  implicit val escalationOnlySlaEncoder: Encoder[EscalationOnlySla] = deriveEncoder[EscalationOnlySla]
  implicit val genericSlaEncoder: Encoder[GenericSla] = deriveEncoder[GenericSla]
  implicit val slaReferenceEncoder: Encoder[SlaReference] = deriveEncoder[SlaReference]
  implicit val slaEncoder: Encoder[Sla] = deriveEncoder[Sla]
  implicit val clusterEncoder: Encoder[Cluster] = deriveEncoder[Cluster]

  implicit val deloymentClusterEncoder: Encoder[DeploymentCluster] = deriveEncoder[DeploymentCluster]
  implicit val abstractClusterEncoder: Encoder[AbstractCluster] = deriveEncoder[AbstractCluster]

  implicit val defaultBlueprintEncoder: Encoder[DefaultBlueprint] = deriveEncoder[DefaultBlueprint]

  implicit val hostEncoder: Encoder[Host] = deriveEncoder[Host]

  implicit val deploymentEncoder: Encoder[Deployment] = deriveEncoder[Deployment]

  implicit val abstractBlueprintEncoder: Encoder[AbstractBlueprint] = deriveEncoder[AbstractBlueprint]

  implicit val blueprintReferenceEncoder: Encoder[BlueprintReference] = deriveEncoder[BlueprintReference]

  implicit val blueprintEncoder: Encoder[Blueprint] = deriveEncoder[Blueprint]

  implicit val templateEncoder: Encoder[Template] = deriveEncoder[Template]

}
