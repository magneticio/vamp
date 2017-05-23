package io.vamp.model.serialization

import io.vamp.common.Namespace
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object NamespaceSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+ new NamespaceSerializer()
}

class NamespaceSerializer extends ArtifactSerializer[Namespace] with TraitDecomposer with ReferenceSerialization with HealthCheckSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case namespace: Namespace ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(namespace.name))
      list += JField("kind", JString(namespace.kind))
      list += JField("config", Extraction.decompose(namespace.config))
      list += JField("metadata", Extraction.decompose(namespace.metadata)(DefaultFormats))
      new JObject(list.toList)
  }
}
