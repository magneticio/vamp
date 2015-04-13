package io.vamp.core.model.serialization

import io.vamp.core.model.artifact._
import org.json4s.FieldSerializer._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BreedSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new PortSerializer() :+
    new DeployableSerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new BreedFieldSerializer()
}

class PortSerializer extends ArtifactSerializer[Port] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case port: Port =>
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(port.name.toString))
      port.alias match {
        case None =>
        case Some(a) => list += JField("alias", JString(a))
      }
      //list += JField("value", JString(port.value))
      new JObject(list.toList)
  }
}

class DeployableSerializer extends ArtifactSerializer[Deployable] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case Deployable(name) => JString(name)
  }
}

class BreedFieldSerializer extends ArtifactFieldSerializer[DefaultBreed] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] =
    ignore("traits") orElse renameTo("environmentVariables", "environment_variables")
}

