package io.vamp.core.model.serialization

import io.vamp.core.model.artifact._
import org.json4s.FieldSerializer._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BlueprintSerializationFormat extends ArtifactSerializationFormat {

  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new BlueprintSerializer() :+
    new SlaSerializer() :+
    new EscalationSerializer() :+
    new ScaleSerializer() :+
    new RoutingSerializer() :+
    new FilterSerializer()

  override def fieldSerializers: List[ArtifactFieldSerializer[_]] = super.fieldSerializers :+
    new ClusterFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference => new JObject(JField("name", JString(blueprint.name)) :: Nil)
    case blueprint: AbstractBlueprint =>
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("endpoints", Extraction.decompose(blueprint.endpoints.map(endpoint => endpoint.name -> endpoint.value).toMap))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster => cluster.name -> cluster).toMap))
      list += JField("parameters", Extraction.decompose(blueprint.parameters))
      new JObject(list.toList)
  }
}

class ClusterFieldSerializer extends ArtifactFieldSerializer[AbstractCluster] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = ignore("name")
}

class SlaSerializer extends ArtifactSerializer[Sla] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case sla: SlaReference => new JObject(JField("name", JString(sla.name)) :: JField("escalations", Extraction.decompose(sla.escalations)) :: Nil)
    case sla: DefaultSla =>
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
    case escalation: DefaultEscalation =>
      val list = new ArrayBuffer[JField]
      if (escalation.name.nonEmpty)
        list += JField("name", JString(escalation.name))
      list += JField("type", JString(escalation.`type`))
      list += JField("parameters", Extraction.decompose(escalation.parameters))
      new JObject(list.toList)
  }
}


class ScaleSerializer extends ArtifactSerializer[Scale] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: ScaleReference => new JObject(JField("name", JString(scale.name)) :: Nil)
    case scale: DefaultScale =>
      val list = new ArrayBuffer[JField]
      if (scale.name.nonEmpty)
        list += JField("name", JString(scale.name))
      list += JField("cpu", JDouble(scale.cpu))
      list += JField("memory", JDouble(scale.memory))
      list += JField("instances", JInt(scale.instances))
      new JObject(list.toList)
  }
}

class RoutingSerializer extends ArtifactSerializer[Routing] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case routing: RoutingReference => new JObject(JField("name", JString(routing.name)) :: Nil)
    case routing: DefaultRouting =>
      val list = new ArrayBuffer[JField]
      if (routing.name.nonEmpty)
        list += JField("name", JString(routing.name))
      if (routing.weight.nonEmpty)
        list += JField("weight", JInt(routing.weight.get))
      list += JField("filters", Extraction.decompose(routing.filters))
      new JObject(list.toList)
  }
}

class FilterSerializer extends ArtifactSerializer[Filter] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case filter: FilterReference => new JObject(JField("name", JString(filter.name)) :: Nil)
    case filter: DefaultFilter =>
      val list = new ArrayBuffer[JField]
      if (filter.name.nonEmpty)
        list += JField("name", JString(filter.name))
      list += JField("condition", JString(filter.condition))
      new JObject(list.toList)
  }
}
