package io.vamp.common.json

import org.json4s._

import scala.language.implicitConversions

object SerializationFormat {

  def apply(formats: io.vamp.common.json.SerializationFormat*): Formats = apply(DefaultFormats, formats: _*)

  def apply(default: Formats, formats: io.vamp.common.json.SerializationFormat*): Formats = {
    formats.foldLeft(default)((f1, f2) ⇒ {
      val serializers = f2.customSerializers.foldLeft(f1)((s1, s2) ⇒ s1 + s2)
      val keySerializers = f2.customKeySerializers.foldLeft(serializers)((s1, s2) ⇒ s1 + s2)
      f2.fieldSerializers.foldLeft(keySerializers)((s1, s2) ⇒ s1 + s2)
    })
  }

  implicit def serializer2serializationFormat(serializer: Serializer[_]): SerializationFormat = new SerializationFormat {
    override def customSerializers = serializer :: super.customSerializers
  }

  implicit def keySerializer2serializationFormat(keySerializer: KeySerializer[_]): SerializationFormat = new SerializationFormat {
    override def customKeySerializers = keySerializer :: super.customKeySerializers
  }

  implicit def fieldSerializer2serializationFormat(fieldSerializer: FieldSerializer[_]): SerializationFormat = new SerializationFormat {
    override def fieldSerializers = fieldSerializer :: super.fieldSerializers
  }

  def unsupported[A]: PartialFunction[(TypeInfo, JValue), A] = {
    case some ⇒ throw new UnsupportedOperationException(s"Cannot deserialize [${some.getClass}]: $some")
  }
}

trait SerializationFormat {
  def customSerializers: List[Serializer[_]] = Nil

  def customKeySerializers: List[KeySerializer[_]] = Nil

  def fieldSerializers: List[FieldSerializer[_]] = Nil
}