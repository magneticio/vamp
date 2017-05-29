package io.vamp.common.json

import io.vamp.common.util.TextUtil
import org.json4s.JsonAST.JObject
import org.json4s._

object MapSerializer extends SerializationFormat {
  override def customSerializers = super.customSerializers :+ new MapSerializer()
}

class MapSerializer extends Serializer[Map[_, _]] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case map: Map[_, _] ⇒
      new JObject(map.map {
        case (name, value) ⇒
          val newName = if (name.toString.contains("-") || name.toString.contains("_")) name.toString else TextUtil.toSnakeCase(name.toString, dash = false)
          JField(newName, Extraction.decompose(value))
      }.toList)
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Map[_, _]] = SerializationFormat.unsupported
}
