package io.vamp.common.json

import org.json4s.JsonAST.{ JObject, JString }
import org.json4s._

object ThrowableSerializer {
  def apply(message: Option[String]): SerializationFormat = new SerializationFormat {
    override def customSerializers = super.customSerializers :+ new ThrowableSerializer(message)
  }
}

class ThrowableSerializer(message: Option[String] = None) extends Serializer[Throwable] {

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case t: Throwable ⇒ new JObject(List(JField("message", JString(message.getOrElse(t.getMessage)))))
  }

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Throwable] = SerializationFormat.unsupported
}

