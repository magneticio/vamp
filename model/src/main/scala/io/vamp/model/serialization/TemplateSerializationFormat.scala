package io.vamp.model.serialization

import io.vamp.common.RootAnyMap
import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object TemplateSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+ new TemplateSerializer()
}

class TemplateSerializer extends ArtifactSerializer[Template] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case template: Template ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(template.name))
      list += JField("kind", JString(template.kind))
      list += JField("metadata", RootAnyMap.toJson(template.metadata))
      list += JField("definition", Extraction.decompose(template.definition)(DefaultFormats))
      new JObject(list.toList)
  }
}
