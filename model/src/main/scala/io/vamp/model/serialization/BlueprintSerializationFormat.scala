package io.vamp.model.serialization

import io.vamp.common.json.SerializationFormat
import io.vamp.model.artifact._
import org.json4s.JsonAST.{ JObject, JString }
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BlueprintSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BlueprintSerializer() :+
    new ScaleSerializer() :+
    new ArgumentSerializer

  override def fieldSerializers = super.fieldSerializers :+
    new ClusterFieldSerializer() :+
    new ServiceFieldSerializer() :+
    new InstanceFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] with TraitDecomposer with ReferenceSerialization with BlueprintGatewaySerializer with DialectSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference ⇒ serializeReference(blueprint)
    case blueprint: AbstractBlueprint ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("kind", JString(blueprint.kind))
      list += JField("metadata", Extraction.decompose(blueprint.metadata)(DefaultFormats))
      list += JField("gateways", serializeGateways(blueprint.gateways))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster ⇒ cluster.name → cluster).toMap))
      list += JField("environment_variables", traits(blueprint.environmentVariables))
      list += JField("dialects", serializeDialects(blueprint.dialects))
      new JObject(list.toList)
  }
}

class ClusterFieldSerializer
    extends ArtifactFieldSerializer[AbstractCluster]
    with DialectSerializer
    with InternalGatewaySerializer
    with HealthCheckSerializer {

  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("name", _)            ⇒ None
    case ("kind", _)            ⇒ None
    case ("gateways", gateways) ⇒ Some(("gateways", serializeGateways(gateways.asInstanceOf[List[Gateway]])))
    case ("healthChecks", Some(healthChecks)) ⇒
      Some(("health_checks", serializeHealthChecks(healthChecks.asInstanceOf[List[HealthCheck]])))
    case ("dialects", dialects) ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[String, Any]])))
  }

}

class ServiceFieldSerializer
    extends ArtifactFieldSerializer[AbstractService]
    with ArgumentListSerializer
    with DialectSerializer
    with TraitDecomposer
    with BlueprintScaleSerializer
    with HealthCheckSerializer {

  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("kind", _)                                    ⇒ None
    case ("environmentVariables", environmentVariables) ⇒ Some(("environment_variables", traitsEnv(environmentVariables.asInstanceOf[List[Trait]])))
    case ("arguments", arguments)                       ⇒ Some(("arguments", serializeArguments(arguments.asInstanceOf[List[Argument]])))
    case ("dialects", dialects)                         ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[String, Any]])))
    case ("scale", Some(scale: Scale))                  ⇒ Some(("scale", serializerScale(scale, full = false)))
    case ("healthChecks", Some(healthChecks))           ⇒ Some(("health_checks", serializeHealthChecks(healthChecks.asInstanceOf[List[HealthCheck]])))
  }

}

class InstanceFieldSerializer extends ArtifactFieldSerializer[Instance] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("kind", _) ⇒ None
  }
}

class ScaleSerializer extends ArtifactSerializer[Scale] with BlueprintScaleSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: Scale ⇒ serializerScale(scale)
  }
}

trait BlueprintScaleSerializer extends ReferenceSerialization {
  def serializerScale(scale: Scale, full: Boolean = true): JObject = scale match {
    case scale: ScaleReference ⇒ serializeReference(scale)
    case scale: DefaultScale ⇒
      val list = new ArrayBuffer[JField]
      if (scale.name.nonEmpty && full) {
        list += JField("name", JString(scale.name))
        list += JField("kind", JString(scale.kind))
        list += JField("metadata", Extraction.decompose(scale.metadata)(DefaultFormats))
      }

      list += JField("cpu", JDouble(scale.cpu.normalized.toDouble))
      list += JField("memory", JString(scale.memory.normalized))
      list += JField("instances", JInt(scale.instances))
      new JObject(list.toList)
  }
}

trait ArgumentListSerializer {
  def serializeArguments(arguments: List[Argument]) = Extraction.decompose(arguments)(SerializationFormat(BlueprintSerializationFormat))
}

trait DialectSerializer {
  def serializeDialects(dialects: Map[String, Any]) = Extraction.decompose(dialects.map({ case (k, v) ⇒ k.toString.toLowerCase → v }))(DefaultFormats)
}

trait BlueprintGatewaySerializer extends GatewayDecomposer {
  def serializeGateways(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name → serializeAnonymousGateway(port = true, event = false)(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}

trait InternalGatewaySerializer extends GatewayDecomposer {
  def serializeGateways(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name → serializeAnonymousGateway(port = false, event = false /* TODO: test if event = true works*/ )(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}

trait HealthCheckSerializer {

  /** Serializes a list of HealthCheck to a single JValue, an JArray with JObjects */
  def serializeHealthChecks(healthChecks: List[HealthCheck]): JValue =
    JArray(healthChecks.map { healthCheck ⇒
      JObject(
        "path" → JString(healthCheck.path),
        "port" → JString(healthCheck.port),
        "initial_delay" → JString(healthCheck.initialDelay.normalized),
        "timeout" → JString(healthCheck.timeout.normalized),
        "interval" → JString(healthCheck.initialDelay.normalized),
        "failures" → JInt(healthCheck.failures),
        "protocol" → JString(healthCheck.protocol)
      )
    })

}
