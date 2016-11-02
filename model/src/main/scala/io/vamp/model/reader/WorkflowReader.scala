package io.vamp.model.reader

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BreedTraitValueValidator
import io.vamp.model.artifact.TimeSchedule.{ RepeatCount, RepeatForever }
import io.vamp.model.artifact._

import scala.util.Try

object WorkflowReader extends YamlReader[Workflow] with ArgumentReader with TraitReader with BreedTraitValueValidator {

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {

    <<![Any]("schedule") match {
      case yaml: YamlSourceReader ⇒

        <<?[Any]("schedule" :: "event" :: Nil) match {
          case None                ⇒
          case Some(tag: String)   ⇒ >>("schedule" :: "event" :: "tags" :: Nil, List(tag))
          case Some(tags: List[_]) ⇒ >>("schedule" :: "event" :: "tags" :: Nil, tags)
          case Some(value)         ⇒ expandToList("schedule" :: "event" :: "tags" :: Nil)
        }

        <<?[Any]("schedule" :: "time" :: Nil) match {
          case None                 ⇒
          case Some(period: String) ⇒ >>("schedule" :: "time" :: "period" :: Nil, period)
          case Some(tags: List[_])  ⇒ >>("schedule" :: "event" :: "tags" :: Nil, tags)
          case _                    ⇒
        }

      case _ ⇒
    }

    expandArguments()

    source
  }

  override protected def parse(implicit source: YamlSourceReader): Workflow = {

    val breed = BreedReader.readReference(<<![Any]("breed"))
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale")

    Workflow(name, breed, status, schedule, scale, environmentVariables(alias = false), arguments(), <<?[String]("network"))
  }

  override protected def validate(workflow: Workflow): Workflow = {
    validateEnvironmentVariablesAgainstBreed(workflow.environmentVariables, workflow.breed)
    validateArguments(workflow.arguments)
    workflow
  }

  private def status(implicit source: YamlSourceReader): Workflow.Status = {
    <<?[String]("status") match {
      case Some(status) if status.toLowerCase == "starting" ⇒ Workflow.Status.Starting
      case Some(status) if status.toLowerCase == "running" ⇒ Workflow.Status.Running
      case Some(status) if status.toLowerCase == "stopping" ⇒ Workflow.Status.Stopping
      case Some(status) if status.toLowerCase == "suspended" ⇒ Workflow.Status.Suspended
      case Some(status) if status.toLowerCase == "suspending" ⇒ Workflow.Status.Suspending
      case Some(status) if status.toLowerCase == "restarting" ⇒ Workflow.Status.Restarting(None)
      case Some(status) ⇒ throwException(IllegalWorkflowStatus(status))
      case None ⇒ Workflow.Status.Starting
    }
  }

  private def schedule(implicit source: YamlSourceReader): Schedule = {
    <<![Any]("schedule") match {
      case "daemon" ⇒ DaemonSchedule
      case yaml: YamlSourceReader if yaml.find[Any]("time").isDefined ⇒ timeSchedule(yaml)
      case yaml: YamlSourceReader if yaml.find[Any]("event").isDefined ⇒ eventSchedule(yaml)
      case other ⇒ throwException(UndefinedWorkflowScheduleError)
    }
  }

  private def timeSchedule(implicit source: YamlSourceReader): Schedule = {

    val period = <<![String]("time" :: "period" :: Nil)

    val repeat = <<?[Int]("time" :: "repeat" :: Nil) match {
      case None        ⇒ RepeatForever
      case Some(count) ⇒ RepeatCount(count)
    }

    val start = <<?[Date]("time" :: "start" :: Nil) map (time ⇒ OffsetDateTime.from(time.toInstant.atZone(ZoneId.of("UTC"))))

    Try(TimeSchedule(period, repeat, start)).getOrElse(throwException(IllegalWorkflowSchedulePeriod(period)))
  }

  private def eventSchedule(implicit source: YamlSourceReader): Schedule = {
    <<?[List[String]]("event" :: "tags" :: Nil).map(tags ⇒ EventSchedule(tags.toSet)).getOrElse(EventSchedule(Set()))
  }
}
