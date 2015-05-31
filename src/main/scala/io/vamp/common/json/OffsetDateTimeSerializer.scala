package io.vamp.common.json

import java.time.format.DateTimeFormatter._
import java.time.{Instant, OffsetDateTime, ZoneId}

import org.json4s.JsonAST.JString
import org.json4s._

object OffsetDateTimeSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new OffsetDateTimeSerializer()

  def toUnixMicro(dateTime: OffsetDateTime): Double = {
    val time = dateTime.toEpochSecond + (dateTime.getNano / 1E+09)
    (math rint time * 1E+06) / 1E+06
  }
}

class OffsetDateTimeSerializer extends CustomSerializer[OffsetDateTime](format => ( {
  case JString(string) => OffsetDateTime.parse(string)
  case JDouble(number) =>
    val epoch = OffsetDateTime.from(Instant.ofEpochSecond(number.toLong).atZone(ZoneId.of("UTC")))
    val micro = ((number % 1) * 1E+06).toLong
    epoch.plusNanos((micro * 1E+03).toLong)
}, {
  case dateTime: OffsetDateTime => JString(dateTime.format(ISO_INSTANT))
}))