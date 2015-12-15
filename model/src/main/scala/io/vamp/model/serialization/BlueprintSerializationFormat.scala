package io.vamp.model.serialization

import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

object BlueprintSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BlueprintSerializer() :+
    new ScaleSerializer() :+
    new GatewaySerializer() :+
    new RoutingStickySerializer() :+
    new RouteSerializer() :+
    new FilterSerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new ClusterFieldSerializer() :+
    new ServiceFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] with TraitDecomposer with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference ⇒ serializeReference(blueprint)
    case blueprint: AbstractBlueprint ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("endpoints", traits(blueprint.endpoints))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster ⇒ cluster.name -> cluster).toMap))
      list += JField("environment_variables", traits(blueprint.environmentVariables))
      new JObject(list.toList)
  }
}

trait DialectSerializer {
  def serializeDialects(dialects: Map[Dialect.Value, Any]) = Extraction.decompose(dialects.map({ case (k, v) ⇒ k.toString.toLowerCase -> v }))(DefaultFormats)
}

class ClusterFieldSerializer extends ArtifactFieldSerializer[AbstractCluster] with DialectSerializer with RoutingSerializer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("name", _)                  ⇒ None
    case ("routing", routing)         ⇒ Some(("routing", serializeRoutings(routing.asInstanceOf[List[DefaultGateway]])))
    case ("portMapping", portMapping) ⇒ Some(("port_mapping", Extraction.decompose(portMapping)(DefaultFormats)))
    case ("dialects", dialects)       ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
  }
}

class ServiceFieldSerializer extends ArtifactFieldSerializer[AbstractService] with DialectSerializer with TraitDecomposer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("environmentVariables", environmentVariables) ⇒ Some(("environment_variables", traits(environmentVariables.asInstanceOf[List[Trait]])))
    case ("dialects", dialects)                         ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
  }
}

class ScaleSerializer extends ArtifactSerializer[Scale] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: ScaleReference ⇒ serializeReference(scale)
    case scale: DefaultScale ⇒
      val list = new ArrayBuffer[JField]
      if (scale.name.nonEmpty)
        list += JField("name", JString(scale.name))
      list += JField("cpu", JDouble(scale.cpu))
      list += JField("memory", JDouble(scale.memory))
      list += JField("instances", JInt(scale.instances))
      new JObject(list.toList)
  }
}

trait RoutingSerializer {
  def serializeRoutings(routings: List[DefaultGateway]) = Extraction.decompose {
    routings.map { routing ⇒
      routing.port.name -> Extraction.decompose(routing)(CoreSerializationFormat.default)
    } toMap
  }(DefaultFormats)
}

class GatewaySerializer extends ArtifactSerializer[Gateway] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case gateway: GatewayReference ⇒ serializeReference(gateway)
    case gateway: DefaultGateway ⇒
      val list = new ArrayBuffer[JField]
      list += JField("sticky", Extraction.decompose(gateway.sticky))
      list += JField("routes", Extraction.decompose {
        gateway.routes.map { route ⇒
          route.path -> route
        } toMap
      })

      new JObject(list.toList)
  }
}

class RoutingStickySerializer extends CustomSerializer[DefaultGateway.Sticky.Value](format ⇒ ({
  case JString(sticky) ⇒ DefaultGateway.Sticky.byName(sticky).getOrElse(throw new UnsupportedOperationException(s"Cannot deserialize sticky value: $sticky"))
}, {
  case sticky: DefaultGateway.Sticky.Value ⇒ JString(sticky.toString.toLowerCase)
}))

class RouteSerializer extends ArtifactSerializer[Route] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case routing: RouteReference ⇒ serializeReference(routing)
    case routing: DefaultRoute ⇒
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
    case filter: FilterReference ⇒ serializeReference(filter)
    case filter: DefaultFilter ⇒
      val list = new ArrayBuffer[JField]
      if (filter.name.nonEmpty)
        list += JField("name", JString(filter.name))
      list += JField("condition", JString(filter.condition))
      new JObject(list.toList)
  }
}
