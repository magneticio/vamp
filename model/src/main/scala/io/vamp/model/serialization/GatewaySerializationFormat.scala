package io.vamp.model.serialization

import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

object GatewaySerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new GatewaySerializer() :+
    new RoutingStickySerializer() :+
    new RouteSerializer() :+
    new ExternalRouteTargetSerializer() :+
    new FilterSerializer() :+
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

      if (full) {
        list += JField("name", JString(gateway.name))
        list += JField("lookup_name", JString(gateway.lookupName))
        list += JField("port", gateway.port.value match {
          case Some(value) ⇒ JString(value)
          case _           ⇒ JString(gateway.port.toValue)
        })
        list += JField("deployed", JBool(gateway.deployed))
      } else if (gateway.port.value.isDefined && gateway.port.name != gateway.port.value.get && !gateway.routes.forall(_.name.isEmpty)) {
        list += JField("port", JString(gateway.port.value.get))
      }

      list += JField("sticky", if (gateway.sticky.isDefined) Extraction.decompose(gateway.sticky) else JString("none"))
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

class RoutingStickySerializer extends CustomSerializer[Gateway.Sticky.Value](format ⇒ ({
  case JString(sticky) ⇒ Gateway.Sticky.byName(sticky).getOrElse(throw new UnsupportedOperationException(s"Cannot deserialize sticky value: $sticky"))
}, {
  case sticky: Gateway.Sticky.Value ⇒ JString(sticky.toString.toLowerCase)
}))

class RouteSerializer extends ArtifactSerializer[Route] with ReferenceSerialization with FilterDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case route: RouteReference ⇒ serializeReference(route)
    case route: DefaultRoute ⇒
      val list = new ArrayBuffer[JField]

      if (route.name.nonEmpty) list += JField("name", JString(route.name))

      list += JField("weight", if (route.weight.isDefined) JString(route.weight.get.normalized) else JNull)
      list += JField("filter_strength", if (route.filterStrength.isDefined) JString(route.filterStrength.get.normalized) else JNull)
      list += JField("filters", JArray(route.filters.map(serializeFilter(full = false))))
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

class FilterSerializer extends ArtifactSerializer[Filter] with FilterDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = serializeFilter(full = true)
}

trait FilterDecomposer extends ReferenceSerialization {

  def serializeFilter(full: Boolean)(implicit format: Formats): PartialFunction[Any, JValue] = {
    case filter: FilterReference ⇒ serializeReference(filter)
    case filter: DefaultFilter ⇒
      val list = new ArrayBuffer[JField]
      if (filter.name.nonEmpty && full) list += JField("name", JString(filter.name))
      list += JField("condition", JString(filter.condition))
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
