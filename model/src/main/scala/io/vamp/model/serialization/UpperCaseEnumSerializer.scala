package io.vamp.model.serialization

import org.json4s.{ Formats, JValue, JsonDSL, Serializer }

import scala.reflect.ClassTag

class UpperCaseEnumSerializer[E <: Enumeration: ClassTag](enum: E) extends Serializer[E#Value] {

  import JsonDSL._

  def deserialize(implicit format: Formats) = throw new NotImplementedError

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case i: E#Value ⇒ i.toString.toUpperCase
  }
}
