package io.vamp.model.artifact

import java.time.{ Duration, OffsetDateTime, Period }

import io.vamp.model.artifact.TimeSchedule.{ Repeat, RepeatForever, RepeatPeriod }
import io.vamp.model.artifact.Workflow.Status.RestartingPhase.RestartingPhaseType

import scala.language.implicitConversions

object Workflow {

  sealed trait Status extends ClassToName

  object Status {

    object Active extends Status

    object Suspended extends Status

    object RestartingPhase extends Enumeration {
      type RestartingPhaseType = Value with ClassToName
      val Stopping, Starting = Value
    }

    case class Restarting(phase: RestartingPhaseType) extends Status

  }

}

case class Workflow(
    name: String,
    breed: Breed,
    status: Workflow.Status,
    schedule: Schedule,
    scale: Option[Scale],
    environmentVariables: List[EnvironmentVariable],
    arguments: List[Argument],
    network: Option[String]) extends Artifact with Lookup {

  val kind = "workflow"
}

sealed trait Schedule

object TimeSchedule {

  sealed trait Repeat

  object RepeatForever extends Repeat

  case class RepeatCount(count: Int) extends Repeat

  case class RepeatPeriod(days: Option[Period], time: Option[Duration]) {

    val format = {
      val period = s"${days.map(_.toString.substring(1)).getOrElse("")}${time.map(_.toString.substring(1)).getOrElse("")}"
      if (period.isEmpty) "PT1S" else s"P$period"
    }

    override def toString = format
  }

  implicit def int2repeat(count: Int): Repeat = RepeatCount(count)

  implicit def string2period(period: String): RepeatPeriod = {
    val trimmed = period.trim

    val (p, d) = (trimmed.indexOf("P"), trimmed.indexOf("T")) match {
      case (periodStart, timeStart) if periodStart == -1 && trimmed.isEmpty ⇒ (None, None)
      case (periodStart, timeStart) if periodStart == -1                    ⇒ throw new RuntimeException(s"invalid period: $trimmed")
      case (periodStart, timeStart) if timeStart == -1                      ⇒ (Option(Period.parse(trimmed.substring(periodStart))), None)
      case (periodStart, timeStart) if periodStart - timeStart == -1        ⇒ (None, Option(Duration.parse(s"P${trimmed.substring(timeStart, trimmed.length())}")))
      case (periodStart, timeStart)                                         ⇒ (Option(Period.parse(trimmed.substring(0, timeStart))), Option(Duration.parse(s"P${trimmed.substring(timeStart, trimmed.length())}")))
    }

    RepeatPeriod(p, d)
  }
}

case class TimeSchedule(period: RepeatPeriod, repeat: Repeat = RepeatForever, start: Option[OffsetDateTime] = None) extends Schedule

case class EventSchedule(tags: Set[String]) extends Schedule

object DaemonSchedule extends Schedule
