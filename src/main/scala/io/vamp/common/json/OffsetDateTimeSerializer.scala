package io.vamp.common.json

import java.time.format.DateTimeFormatter._
import java.time.{Instant, OffsetDateTime, ZoneId}

import org.json4s.JsonAST.JString
import org.json4s._

object OffsetDateTimeSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new OffsetDateTimeSerializer()
}

class OffsetDateTimeSerializer extends CustomSerializer[OffsetDateTime](format => ( {
  case JString(string) => OffsetDateTime.parse(string)
  case JDouble(number) => OffsetDateTime.from(Instant.ofEpochSecond(number.toLong).atZone(ZoneId.of("UTC")))
}, {
  case dateTime: OffsetDateTime => JString(dateTime.format(ISO_INSTANT))
}))