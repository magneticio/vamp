package io.vamp.core.model.serialization

import io.vamp.core.model.artifact._
import org.json4s.FieldSerializer._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BreedSerializationFormat extends ArtifactSerializationFormat {

  override def customSerializers = super.customSerializers :+
    new TraitNameSerializer() :+
    new TraitDirectionSerializer() :+
    new PortSerializer() :+
    new DeployableSerializer()

  override def customKeySerializers = super.customKeySerializers :+
    new TraitNameKeySerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new BreedFieldSerializer()
}

class TraitNameSerializer extends ArtifactSerializer[Trait.Name] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case name: Trait.Name => JString(name.toString)
  }
}

class TraitNameKeySerializer extends ArtifactKeySerializer[Trait.Name] {
  override def serialize(implicit format: Formats): PartialFunction[Any, String] = {
    case name: Trait.Name => name.toString
  }
}

class TraitDirectionSerializer extends ArtifactSerializer[Trait.Direction.Value] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case direction: Trait.Direction.Value => JString(direction.toString.toUpperCase)
  }
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
      list += JField("value", JString(port.valueAsString))
      list += JField("direction", JString(port.direction.toString.toUpperCase))
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

