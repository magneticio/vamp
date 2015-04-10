package io.vamp.common.json

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import org.json4s.JsonAST.JString
import org.json4s._

object OffsetDateTimeSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new OffsetDateTimeSerializer()
}

class OffsetDateTimeSerializer extends Serializer[OffsetDateTime] {
  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case timestamp: OffsetDateTime => JString(timestamp.format(DateTimeFormatter.ISO_INSTANT))
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), OffsetDateTime] = {
    case (_, timestamp: JString) => OffsetDateTime.parse(timestamp.values, DateTimeFormatter.ISO_INSTANT)
  }
}
