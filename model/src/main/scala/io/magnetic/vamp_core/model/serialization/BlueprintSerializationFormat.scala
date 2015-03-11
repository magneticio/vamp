package io.magnetic.vamp_core.model.serialization

import io.magnetic.vamp_core.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BlueprintSerializationFormat extends ArtifactSerializationFormat {

  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new ScaleSerializer()
}

class ScaleSerializer extends ArtifactSerializer[Scale] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: ScaleReference => new JObject(JField("name", JString(scale.name)) :: Nil)
    case scale: DefaultScale =>
      val list = new ArrayBuffer[JField]
      if (scale.name.nonEmpty)
        list += JField("name", JString(scale.name))
      list += JField("cpu", JDouble(scale.cpu))
      list += JField("memory", JDouble(scale.memory))
      list += JField("instances", JInt(scale.instances))
      new JObject(list.toList)
  }
}
