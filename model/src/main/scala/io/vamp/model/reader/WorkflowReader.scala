package io.vamp.model.reader

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.workflow.TimeSchedule.{ RepeatCount, RepeatForever }
import io.vamp.model.workflow._

import scala.util.Try

object WorkflowReader extends YamlReader[Workflow] {

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

    source
  }

  override protected def parse(implicit source: YamlSourceReader): Workflow = {

    val breed = BreedReader.readReference(<<![Any]("breed"))
    val scale = ScaleReader.readOptionalReferenceOrAnonymous("scale")

    Workflow(name, breed, schedule, scale)
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
