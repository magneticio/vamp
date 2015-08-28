package io.vamp.core.model.serialization

import io.vamp.core.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BlueprintSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BlueprintSerializer() :+
    new ScaleSerializer() :+
    new RoutingSerializer() :+
    new FilterSerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new ClusterFieldSerializer() :+
    new ServiceFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] with TraitDecomposer with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference => serializeReference(blueprint)
    case blueprint: AbstractBlueprint =>
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("endpoints", traits(blueprint.endpoints))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster => cluster.name -> cluster).toMap))
      list += JField("environment_variables", traits(blueprint.environmentVariables))
      new JObject(list.toList)
  }
}

trait DialectSerializer {
  def serializeDialects(dialects: Map[Dialect.Value, Any]) = {
    implicit val formats = DefaultFormats
    Extraction.decompose(dialects.map({ case (k, v) => k.toString.toLowerCase -> v }))
  }
}

class ClusterFieldSerializer extends ArtifactFieldSerializer[AbstractCluster] with DialectSerializer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("name", _) => None
    case ("dialects", dialects) => Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
  }
}

class ServiceFieldSerializer extends ArtifactFieldSerializer[AbstractService] with DialectSerializer with TraitDecomposer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("environmentVariables", environmentVariables) => Some(("environment_variables", traits(environmentVariables.asInstanceOf[List[Trait]])))
    case ("dialects", dialects) => Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
  }
}

class ScaleSerializer extends ArtifactSerializer[Scale] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: ScaleReference => serializeReference(scale)
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

class RoutingSerializer extends ArtifactSerializer[Routing] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case routing: RoutingReference => serializeReference(routing)
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

class FilterSerializer extends ArtifactSerializer[Filter] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case filter: FilterReference => serializeReference(filter)
    case filter: DefaultFilter =>
      val list = new ArrayBuffer[JField]
      if (filter.name.nonEmpty)
        list += JField("name", JString(filter.name))
      list += JField("condition", JString(filter.condition))
      new JObject(list.toList)
  }
}
