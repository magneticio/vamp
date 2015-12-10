package io.vamp.common.json

import io.vamp.common.text.Text
import org.json4s.JsonAST.JObject
import org.json4s._

object MapSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new MapSerializer()
}

class MapSerializer extends Serializer[Map[_, _]] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case map: Map[_, _] ⇒
      new JObject(map.map {
        case (name, value) ⇒ JField(Text.toSnakeCase(name.toString, dash = false), Extraction.decompose(value))
      }.toList)
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Map[_, _]] = SerializationFormat.unsupported
}
