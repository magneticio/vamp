package io.vamp.model.workflow

import java.time.{ Period, Duration, OffsetDateTime }

import io.vamp.model.artifact.{ Scale, Lookup, Artifact, Reference }
import io.vamp.model.workflow.TimeTrigger.{ RepeatTimes, RepeatPeriod, RepeatForever }
import scala.language.implicitConversions

trait Workflow extends Artifact

case class WorkflowReference(name: String) extends Reference with Workflow

case class DefaultWorkflow(name: String, containerImage: Option[String], script: Option[String], command: Option[String], scale: Option[Scale]) extends Workflow

trait Trigger

object TimeTrigger {

  sealed trait RepeatTimes

  object RepeatForever extends RepeatTimes

  case class RepeatTimesCount(count: Int) extends RepeatTimes

  case class RepeatPeriod(days: Option[Period], time: Option[Duration]) {

    val format = {
      val period = s"${days.map(_.toString.substring(1)).getOrElse("")}${time.map(_.toString.substring(1)).getOrElse("")}"
      if (period.isEmpty) "PT1S" else s"P$period"
    }

    override def toString = format
  }

  implicit def int2repeat(count: Int): RepeatTimes = RepeatTimesCount(count)

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

case class TimeTrigger(period: RepeatPeriod, repeatTimes: RepeatTimes = RepeatForever, startTime: Option[OffsetDateTime] = None) extends Trigger

case class DeploymentTrigger(deployment: String) extends Trigger

case class EventTrigger(tags: Set[String]) extends Trigger

case class ScheduledWorkflow(name: String, workflow: Workflow, trigger: Trigger) extends Artifact with Lookup