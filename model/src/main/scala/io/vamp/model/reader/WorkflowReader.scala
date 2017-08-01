package io.vamp.model.reader

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BreedTraitValueValidator
import io.vamp.model.artifact.TimeSchedule.{ RepeatCount, RepeatForever }
import io.vamp.model.artifact._

import scala.util.Try

object WorkflowReader extends YamlReader[Workflow] with ArgumentReader with TraitReader with DialectReader with BreedTraitValueValidator {

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {

    <<![Any]("schedule") match {
      case _: YamlSourceReader ⇒

        <<?[Any]("schedule" :: "event" :: Nil) match {
          case None                ⇒
          case Some(tag: String)   ⇒ >>("schedule" :: "event" :: "tags" :: Nil, List(tag))
          case Some(tags: List[_]) ⇒ >>("schedule" :: "event" :: "tags" :: Nil, tags)
          case Some(_)             ⇒ expandToList("schedule" :: "event" :: "tags" :: Nil)
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
    source.flatten({ entry ⇒ entry == "health" })

    val breed = BreedReader.readReference(<<![Any]("breed"))
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale")
    val network = <<?[String]("network")

    Workflow(
      name,
      metadata,
      breed,
      status,
      schedule,
      scale,
      environmentVariables(alias = false),
      arguments(),
      network,
      HealthCheckReader.read,
      dialects,
      DeploymentReader.parseInstances
    )
  }

  override protected def validate(workflow: Workflow): Workflow = {
    validateEnvironmentVariablesAgainstBreed(workflow.environmentVariables, workflow.breed)
    workflow
  }

  private def status(implicit source: YamlSourceReader): Workflow.Status = WorkflowStatusReader.status(<<?[String]("status"))

  private def schedule(implicit source: YamlSourceReader): Schedule = {
    <<![Any]("schedule") match {
      case "daemon" ⇒ DaemonSchedule
      case yaml: YamlSourceReader if yaml.find[Any]("time").isDefined ⇒ timeSchedule(yaml)
      case yaml: YamlSourceReader if yaml.find[Any]("event").isDefined ⇒ eventSchedule(yaml)
      case _ ⇒ throwException(UndefinedWorkflowScheduleError)
    }
  }

  private def timeSchedule(implicit source: YamlSourceReader): Schedule = {

    val period = <<![String]("time" :: "period" :: Nil)

    val repeat = <<?[Int]("time" :: "repeat" :: Nil) match {
      case None        ⇒ RepeatForever
      case Some(count) ⇒ RepeatCount(count)
    }

    val start = <<?[Any]("time" :: "start" :: Nil) match {
      case Some(time: String) if time.trim == "now" ⇒ None
      case Some(time: Date)                         ⇒ Option(OffsetDateTime.from(time.toInstant.atZone(ZoneId.of("UTC"))))
      case Some(time)                               ⇒ Option(OffsetDateTime.parse(time.toString))
      case None                                     ⇒ None
    }

    Try(TimeSchedule(period, repeat, start)).getOrElse(throwException(IllegalWorkflowSchedulePeriod(period)))
  }

  private def eventSchedule(implicit source: YamlSourceReader): Schedule = {
    <<?[List[String]]("event" :: "tags" :: Nil).map(tags ⇒ EventSchedule(tags.toSet)).getOrElse(EventSchedule(Set()))
  }
}

object WorkflowStatusReader extends ModelNotificationProvider {

  def status(value: String): Workflow.Status = value match {
    case s if s.toLowerCase == "starting"   ⇒ Workflow.Status.Starting
    case s if s.toLowerCase == "running"    ⇒ Workflow.Status.Running
    case s if s.toLowerCase == "stopping"   ⇒ Workflow.Status.Stopping
    case s if s.toLowerCase == "suspended"  ⇒ Workflow.Status.Suspended
    case s if s.toLowerCase == "suspending" ⇒ Workflow.Status.Suspending
    case s if s.toLowerCase == "restarting" ⇒ Workflow.Status.Restarting(None)
    case s                                  ⇒ throwException(IllegalWorkflowStatus(s))
  }

  def status(value: Option[String]): Workflow.Status = value match {
    case Some(s) ⇒ status(s)
    case None    ⇒ Workflow.Status.Starting
  }

  def phase(value: Option[String]): Option[Workflow.Status.RestartingPhase.Value] = value.flatMap {
    case "Stopping" ⇒ Option(Workflow.Status.RestartingPhase.Stopping)
    case "Starting" ⇒ Option(Workflow.Status.RestartingPhase.Starting)
    case _          ⇒ None
  }
}