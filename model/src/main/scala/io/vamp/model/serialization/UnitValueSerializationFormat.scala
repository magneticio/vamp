package io.vamp.model.serialization

import io.vamp.model.reader.UnitValue
import org.json4s.JsonAST.JString
import org.json4s._

import scala.language.postfixOps

object UnitValueSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers: List[Serializer[_]] = super.customSerializers :+ new UnitValueSerializer()
}

class UnitValueSerializer extends CustomSerializer[UnitValue[_]](_ ⇒ ({
  case JString(string) ⇒ UnitValue.of(string).get
}, {
  case unit: UnitValue[_] ⇒ JString(unit.normalized)
}))
