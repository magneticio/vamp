package io.magnetic.vamp_common.json

import java.time.format.DateTimeFormatter._
import java.time.{Instant, OffsetDateTime, ZoneId}

import org.json4s.JsonAST.{JDouble, JString}
import org.json4s.{CustomSerializer, DefaultFormats}

class OffsetDateTimeSerializer extends CustomSerializer[OffsetDateTime](
  format => (
    {
      case JString(str) => OffsetDateTime.parse(str)
      case JDouble(num) =>
        OffsetDateTime.from(Instant.ofEpochSecond(num.toLong).atZone(ZoneId.of("UTC")))

    },
    { case date: OffsetDateTime => JString(date.format(ISO_OFFSET_DATE_TIME))}
    )
)

object Serializers {
  val formats = DefaultFormats + new OffsetDateTimeSerializer()
}
