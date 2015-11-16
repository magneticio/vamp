package io.vamp.common.json

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter._

import org.json4s.JsonAST.JString
import org.json4s._

object OffsetDateTimeSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new OffsetDateTimeSerializer()
}

class OffsetDateTimeSerializer extends CustomSerializer[OffsetDateTime](format ⇒ ({
  case JString(string) ⇒ OffsetDateTime.parse(string)
}, {
  case dateTime: OffsetDateTime ⇒ JString(dateTime.format(ISO_OFFSET_DATE_TIME))
}))