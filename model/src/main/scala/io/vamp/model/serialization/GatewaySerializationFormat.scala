package io.vamp.model.serialization

import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

object GatewaySerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new GatewaySerializer() :+
    new GatewayStickySerializer() :+
    new RouteSerializer() :+
    new ExternalRouteTargetSerializer() :+
    new ConditionSerializer() :+
    new RewriteSerializer()
}

class GatewaySerializer extends ArtifactSerializer[Gateway] with GatewayDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = serializeGateway
}

trait GatewayDecomposer extends ReferenceSerialization {

  def serializeGateway(implicit format: Formats): PartialFunction[Any, JValue] = serialize(full = true)

  def serializeAnonymousGateway(implicit format: Formats): PartialFunction[Any, JValue] = serialize(full = false)

  private def serialize(full: Boolean)(implicit format: Formats): PartialFunction[Any, JValue] = {
    case gateway: Gateway ⇒
      val list = new ArrayBuffer[JField]

      if (gateway.name.nonEmpty) {
        list += JField("name", JString(gateway.name))
        list += JField(Lookup.entry, JString(gateway.lookupName))
      }

      if (full) {
        list += JField("port", gateway.port.value match {
          case Some(value) ⇒ JString(value)
          case _           ⇒ JString(gateway.port.toValue)
        })

        list += JField("inner", JBool(gateway.inner))

        if (gateway.service.isDefined) {
          val serviceHost = JField("host", JString(gateway.service.get.host))
          val servicePort = JField("port", gateway.service.get.port.value match {
            case Some(value) ⇒ JString(value)
            case _           ⇒ JString(gateway.port.toValue)
          })
          list += JField("service", new JObject(serviceHost :: servicePort :: Nil))
        }

        list += JField("deployed", JBool(gateway.deployed))
      } else if (gateway.port.value.isDefined && gateway.port.name != gateway.port.value.get) {
        list += JField("port", JString(gateway.port.value.get))
      }

      list += JField("sticky", if (gateway.sticky.isDefined) Extraction.decompose(gateway.sticky) else JString("none"))
      list += JField("virtual_hosts", Extraction.decompose(gateway.virtualHosts))
      list += JField("routes", Extraction.decompose {
        gateway.routes.map { route ⇒
          (route.path.segments match {
            case _ :: _ :: s :: _ :: Nil if !full ⇒ s
            case _ :: _ :: _ :: Nil if !full      ⇒ GatewayPath(route.path.segments.tail).normalized
            case _                                ⇒ route.path.source
          }) -> route
        } toMap
      })

      new JObject(list.toList)
  }
}

class GatewayStickySerializer extends CustomSerializer[Gateway.Sticky.Value](format ⇒ ({
  case JString(sticky) ⇒ Gateway.Sticky.byName(sticky).getOrElse(throw new UnsupportedOperationException(s"Cannot deserialize sticky value: $sticky"))
}, {
  case sticky: Gateway.Sticky.Value ⇒ JString(sticky.toString.toLowerCase)
}))

class RouteSerializer extends ArtifactSerializer[Route] with ReferenceSerialization with ConditionDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case route: RouteReference ⇒ serializeReference(route)
    case route: DefaultRoute ⇒
      val list = new ArrayBuffer[JField]

      if (route.name.nonEmpty) list += JField("name", JString(route.name))

      list += JField("weight", if (route.weight.isDefined) JString(route.weight.get.normalized) else JNull)
      list += JField("balance", if (route.balance.isDefined) JString(route.balance.get) else JString(DefaultRoute.defaultBalance))
      list += JField("condition_strength", if (route.conditionStrength.isDefined) JString(route.conditionStrength.get.normalized) else JNull)
      list += JField("conditions", JArray(route.conditions.map(serializeCondition(full = false))))
      list += JField("rewrites", Extraction.decompose(route.rewrites))

      if (route.targets.nonEmpty) list += JField("instances", Extraction.decompose(route.targets))

      new JObject(list.toList)
  }
}

class ExternalRouteTargetSerializer extends ArtifactSerializer[ExternalRouteTarget] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case target: ExternalRouteTarget ⇒
      val list = new ArrayBuffer[JField]
      list += JField("url", JString(target.url))
      new JObject(list.toList)
  }
}

class ConditionSerializer extends ArtifactSerializer[Condition] with ConditionDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = serializeCondition(full = true)
}

trait ConditionDecomposer extends ReferenceSerialization {

  def serializeCondition(full: Boolean)(implicit format: Formats): PartialFunction[Any, JValue] = {
    case condition: ConditionReference ⇒ serializeReference(condition)
    case condition: DefaultCondition ⇒
      val list = new ArrayBuffer[JField]
      if (condition.name.nonEmpty && full) list += JField("name", JString(condition.name))
      list += JField("condition", JString(condition.definition))
      new JObject(list.toList)
  }
}

class RewriteSerializer extends ArtifactSerializer[Rewrite] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case rewrite: RewriteReference ⇒ serializeReference(rewrite)
    case rewrite: PathRewrite ⇒
      val list = new ArrayBuffer[JField]
      if (rewrite.name.nonEmpty)
        list += JField("name", JString(rewrite.name))
      list += JField("path", JString(rewrite.definition))
      new JObject(list.toList)
  }
}
