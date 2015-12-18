package io.vamp.model.serialization

import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

object BlueprintSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BlueprintSerializer() :+
    new ScaleSerializer()

  override def fieldSerializers = super.fieldSerializers :+
    new ClusterFieldSerializer() :+
    new ServiceFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] with TraitDecomposer with ReferenceSerialization with BlueprintGatewaySerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference ⇒ serializeReference(blueprint)
    case blueprint: AbstractBlueprint ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("gateways", serializeGateways(blueprint.gateways))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster ⇒ cluster.name -> cluster).toMap))
      list += JField("environment_variables", traits(blueprint.environmentVariables))
      new JObject(list.toList)
  }
}

class ClusterFieldSerializer extends ArtifactFieldSerializer[AbstractCluster] with DialectSerializer with RoutingSerializer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("name", _)                  ⇒ None
    case ("routing", routing)         ⇒ Some(("routing", serializeRoutings(routing.asInstanceOf[List[Gateway]])))
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

trait DialectSerializer {
  def serializeDialects(dialects: Map[Dialect.Value, Any]) = Extraction.decompose(dialects.map({ case (k, v) ⇒ k.toString.toLowerCase -> v }))(DefaultFormats)
}

trait BlueprintGatewaySerializer extends GatewayDecomposer {
  def serializeGateways(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name -> serializeAnonymousGateway(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}

trait RoutingSerializer extends GatewayDecomposer {
  def serializeRoutings(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name -> serializeAnonymousGateway(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}
