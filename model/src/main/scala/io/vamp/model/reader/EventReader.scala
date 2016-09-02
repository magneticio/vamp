package io.vamp.model.reader

import java.time.OffsetDateTime

import io.vamp.model.event.{ Aggregator, Event, EventQuery, TimeRange }
import io.vamp.model.notification.{ EventTimestampError, UnsupportedAggregatorError }
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.EventValidator

object EventReader extends YamlReader[Event] with EventValidator {

  override protected def expand(implicit source: YamlSourceReader) = {
    expandToList("tags")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): Event = {
    val tags = <<![List[String]]("tags").toSet
    val value = <<?[AnyRef]("value") match {
      case None                         ⇒ None
      case Some(yaml: YamlSourceReader) ⇒ yaml.flatten()
      case Some(any)                    ⇒ any
    }

    val timestamp = <<?[String]("timestamp") match {
      case None ⇒ OffsetDateTime.now
      case Some(time) ⇒ try OffsetDateTime.parse(time) catch {
        case e: Exception ⇒ throwException(EventTimestampError(time))
      }
    }

    val `type` = <<?[String]("type").getOrElse(Event.defaultType)

    Event(tags, value, timestamp, `type`)
  }

  override def validate(event: Event): Event = validateEvent(event)
}

object EventQueryReader extends YamlReader[EventQuery] with EventValidator {

  override protected def expand(implicit source: YamlSourceReader) = {
    expandToList("tags")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): EventQuery = {
    val tags = <<![List[String]]("tags").toSet

    val timestamp = <<?[Any]("timestamp").flatMap { _ ⇒
      Some(TimeRange(<<?[String]("timestamp" :: "lt"), <<?[String]("timestamp" :: "lte"), <<?[String]("timestamp" :: "gt"), <<?[String]("timestamp" :: "gte")))
    }

    val aggregator = <<?[Any]("aggregator") match {
      case None ⇒ None
      case Some(_) ⇒
        val `type` = <<![String]("aggregator" :: "type").toLowerCase
        Aggregator.values.find(agg ⇒ agg.toString.toLowerCase == `type`) match {
          case None      ⇒ throwException(UnsupportedAggregatorError(`type`))
          case Some(agg) ⇒ Some(Aggregator(agg, <<?[String]("aggregator" :: "field")))
        }
    }

    EventQuery(tags, timestamp, aggregator)
  }

  override def validate(eventQuery: EventQuery): EventQuery = validateEventQuery(eventQuery)
}