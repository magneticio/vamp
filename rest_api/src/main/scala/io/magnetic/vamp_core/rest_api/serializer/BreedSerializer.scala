package io.magnetic.vamp_core.rest_api.serializer

import io.magnetic.vamp_core.model.artifact.{Deployable, Port, Trait}
import org.json4s.JsonAST.JString
import org.json4s._
import org.json4s.native.Serialization

import scala.collection.mutable.ArrayBuffer

object BreedSerializer {
  def formats: Formats = Serialization.formats(NoTypeHints) +
    new TraitNameSerializer() +
    new TraitNameKeySerializer() +
    new TraitDirectionSerializer() +
    new PortSerializer() +
    new DeployableSerializer()
}

class TraitNameSerializer extends CustomSerializer[Trait.Name](format => ( {
  case JString(value) => throw new UnsupportedOperationException()
}, {
  case name: Trait.Name => JString(name.toString)
}))

class TraitNameKeySerializer extends CustomKeySerializer[Trait.Name](format => ( {
  case value => throw new UnsupportedOperationException()
}, {
  case name: Trait.Name => name.toString
}))

class TraitDirectionSerializer extends CustomSerializer[Trait.Direction.Value](format => ( {
  case JString(value) => throw new UnsupportedOperationException()
}, {
  case direction: Trait.Direction.Value => JString(direction.toString.toUpperCase)
}))

class PortSerializer extends CustomSerializer[Port](format => ( {
  case JString(value) => throw new UnsupportedOperationException()
}, {
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
}))

class DeployableSerializer extends CustomSerializer[Deployable](format => ( {
  case JString(value) => throw new UnsupportedOperationException()
}, {
  case Deployable(name) => JString(name)
}))
