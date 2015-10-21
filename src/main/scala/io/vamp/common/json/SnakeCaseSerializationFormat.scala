package io.vamp.common.json

import io.vamp.common.text.Text
import org.json4s._

object SnakeCaseSerializationFormat extends SerializationFormat {
  override def fieldSerializers = super.fieldSerializers :+ new SnakeCaseFieldSerializer()
}

class SnakeCaseFieldSerializer extends FieldSerializer[Any] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case (name, x) ⇒ Some(Text.toSnakeCase(name, dash = false) -> x)
  }
}
