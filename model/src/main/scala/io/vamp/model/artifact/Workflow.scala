package io.vamp.model.artifact

import java.time.{ Duration, OffsetDateTime, Period }

import io.vamp.common.{ Artifact, Lookup, RootAnyMap }
import io.vamp.model.artifact.TimeSchedule.{ Repeat, RepeatForever, RepeatPeriod }

import scala.language.implicitConversions

object Workflow {

  val kind: String = "workflows"

  sealed trait Status {

    def describe: String = toString

    override def toString: String = {
      val clazz = getClass.toString
      val clean = if (clazz.endsWith("$")) clazz.substring(0, clazz.length - 1) else clazz
      clean.substring(clean.lastIndexOf('$') + 1).toLowerCase
    }
  }

  object Status {

    object Starting extends Status

    object Stopping extends Status

    object Running extends Status

    object Suspended extends Status

    object Suspending extends Status

    case class Restarting(phase: Option[RestartingPhase.Value]) extends Status {
      override def describe: String = {
        phase.map(p ⇒ s"$toString/${p.toString.toLowerCase}").getOrElse(toString)
      }
    }

    object RestartingPhase extends Enumeration {
      val Stopping, Starting = Value
    }
  }
}

case class Workflow(
    name:                 String,
    metadata:             RootAnyMap,
    breed:                Breed,
    status:               Workflow.Status,
    schedule:             Schedule,
    scale:                Option[Scale],
    environmentVariables: List[EnvironmentVariable],
    arguments:            List[Argument],
    network:              Option[String],
    healthChecks:         Option[List[HealthCheck]],
    dialects:             Map[String, Any]          = Map(),
    instances:            List[Instance]            = Nil,
    health:               Option[Health]            = None
) extends Artifact with Lookup {
  val kind: String = Workflow.kind
}

sealed trait Schedule

object TimeSchedule {

  sealed trait Repeat

  object RepeatForever extends Repeat

  case class RepeatCount(count: Int) extends Repeat

  case class RepeatPeriod(days: Option[Period], time: Option[Duration]) {

    val format: String = {
      val period = s"${days.map(_.toString.substring(1)).getOrElse("")}${time.map(_.toString.substring(1)).getOrElse("")}"
      if (period.isEmpty) "PT1S" else s"P$period"
    }

    override def toString: String = format
  }

  implicit def int2repeat(count: Int): Repeat = RepeatCount(count)

  implicit def string2period(period: String): RepeatPeriod = {
    val trimmed = period.trim

    val (p, d) = (trimmed.indexOf("P"), trimmed.indexOf("T")) match {
      case (periodStart, _) if periodStart == -1 && trimmed.isEmpty ⇒ (None, None)
      case (periodStart, _) if periodStart == -1 ⇒ throw new RuntimeException(s"invalid period: $trimmed")
      case (periodStart, timeStart) if timeStart == -1 ⇒ (Option(Period.parse(trimmed.substring(periodStart))), None)
      case (periodStart, timeStart) if periodStart - timeStart == -1 ⇒ (None, Option(Duration.parse(s"P${trimmed.substring(timeStart, trimmed.length())}")))
      case (_, timeStart) ⇒ (Option(Period.parse(trimmed.substring(0, timeStart))), Option(Duration.parse(s"P${trimmed.substring(timeStart, trimmed.length())}")))
    }

    RepeatPeriod(p, d)
  }
}

case class TimeSchedule(period: RepeatPeriod, repeat: Repeat = RepeatForever, start: Option[OffsetDateTime] = None) extends Schedule

case class EventSchedule(tags: Set[String]) extends Schedule

object DaemonSchedule extends Schedule
