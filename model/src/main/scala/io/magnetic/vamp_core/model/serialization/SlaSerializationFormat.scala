package io.magnetic.vamp_core.model.serialization

import io.magnetic.vamp_core.model.artifact._
import org.json4s.FieldSerializer._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object SlaSerializationFormat extends ArtifactSerializationFormat {

  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new SlaSerializer() :+
    new EscalationSerializer()

  override def fieldSerializers: List[ArtifactFieldSerializer[_]] = super.fieldSerializers :+
    new EscalationFieldSerializer()
}

class SlaSerializer extends ArtifactSerializer[Sla] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case sla: SlaReference => new JObject(JField("name", JString(sla.name)) :: JField("escalations", Extraction.decompose(sla.escalations)) :: Nil)

    case sla: ResponseTimeSlidingWindowSla =>
      val list = new ArrayBuffer[JField]
      if (sla.name.nonEmpty)
        list += JField("name", JString(sla.name))
      list += JField("type", JString("response_time_sliding_window"))
      list += JField("window", Extraction.decompose(Map("interval" -> sla.interval.toSeconds, "cooldown" -> sla.cooldown.toSeconds)))
      list += JField("threshold", Extraction.decompose(Map("upper" -> sla.upper.toMillis, "lower" -> sla.lower.toMillis)))
      list += JField("escalations", Extraction.decompose(sla.escalations))
      new JObject(list.toList)

    case sla: GenericSla =>
      val list = new ArrayBuffer[JField]
      if (sla.name.nonEmpty)
        list += JField("name", JString(sla.name))
      list += JField("type", JString(sla.`type`))
      list += JField("parameters", Extraction.decompose(sla.parameters))
      list += JField("escalations", Extraction.decompose(sla.escalations))
      new JObject(list.toList)
  }
}

class EscalationSerializer extends ArtifactSerializer[Escalation] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case escalation: EscalationReference => new JObject(JField("name", JString(escalation.name)) :: Nil)

    case escalation: ScaleInstancesEscalation =>
      val list = new ArrayBuffer[JField]
      if (escalation.name.nonEmpty)
        list += JField("name", JString(escalation.name))
      list += JField("type", JString("scale_instances"))
      list += JField("minimum", JInt(escalation.minimum))
      list += JField("maximum", JInt(escalation.maximum))
      list += JField("scale_by", JInt(escalation.scaleBy))
      new JObject(list.toList)

    case escalation: ScaleCpuEscalation =>
      val list = new ArrayBuffer[JField]
      if (escalation.name.nonEmpty)
        list += JField("name", JString(escalation.name))
      list += JField("type", JString("scale_cpu"))
      list += JField("minimum", JDouble(escalation.minimum))
      list += JField("maximum", JDouble(escalation.maximum))
      list += JField("scale_by", JDouble(escalation.scaleBy))
      new JObject(list.toList)

    case escalation: ScaleMemoryEscalation =>
      val list = new ArrayBuffer[JField]
      if (escalation.name.nonEmpty)
        list += JField("name", JString(escalation.name))
      list += JField("type", JString("scale_memory"))
      list += JField("minimum", JDouble(escalation.minimum))
      list += JField("maximum", JDouble(escalation.maximum))
      list += JField("scale_by", JDouble(escalation.scaleBy))
      new JObject(list.toList)

    case escalation: GenericEscalation =>
      val list = new ArrayBuffer[JField]
      if (escalation.name.nonEmpty)
        list += JField("name", JString(escalation.name))
      list += JField("type", JString(escalation.`type`))
      list += JField("parameters", Extraction.decompose(escalation.parameters))
      new JObject(list.toList)
  }
}

class EscalationFieldSerializer extends ArtifactFieldSerializer[ScaleEscalation[_]] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = renameTo("scaleBy", "scale_by")
}