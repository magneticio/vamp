package io.vamp.common.json

import org.json4s.{FieldSerializer, KeySerializer, Serializer}


trait SerializationFormat {
  def customSerializers: List[Serializer[_]] = Nil

  def customKeySerializers: List[KeySerializer[_]] = Nil

  def fieldSerializers: List[FieldSerializer[_]] = Nil
}