package io.vamp.core.model.reader

import java.time.OffsetDateTime

import io.vamp.core.model.event.Event
import io.vamp.core.model.validator.EventValidator

import scala.language.postfixOps
import scala.util.Try

object EventReader extends YamlReader[Event] with EventValidator {

  override protected def expand(implicit source: YamlObject) = {
    expandToList("tags")
    source
  }

  override protected def parse(implicit source: YamlObject): Event = {
    val tags = <<![List[String]]("tags").toSet
    val value = <<![AnyRef]("value")
    val timestamp = Try(OffsetDateTime.parse(<<![String]("timestamp"))) getOrElse OffsetDateTime.now
    val `type` = <<?[String]("type").getOrElse("event")

    Event(tags, value, timestamp, `type`)
  }

  override def validate(event: Event): Event = validateEvent(event)
}

