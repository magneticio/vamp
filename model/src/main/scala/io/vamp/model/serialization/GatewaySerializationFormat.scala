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
    new FilterSerializer()
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
        list += JField("port", if (gateway.port.value.isDefined) JString(gateway.port.value.get) else JNull)
        list += JField("active", JBool(gateway.active))
      } else if (gateway.port.value.isDefined && gateway.port.name != gateway.port.value.get && !gateway.inner) {
        list += JField("port", JString(gateway.port.value.get))
      }

      list += JField("sticky", if (gateway.sticky.isDefined) Extraction.decompose(gateway.sticky) else JString("none"))
      list += JField("routes", Extraction.decompose {
        gateway.routes.map { route ⇒
          (route.path.segments match {
            case _ :: _ :: s :: _ :: Nil if !full ⇒ s
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

class RouteSerializer extends ArtifactSerializer[Route] with ReferenceSerialization {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case route: RouteReference ⇒ serializeReference(route)
    case route: AbstractRoute ⇒
      val list = new ArrayBuffer[JField]

      if (route.name.nonEmpty)
        list += JField("name", JString(route.name))

      list += JField("weight", if (route.weight.isDefined) JString(route.weight.get.normalized) else JNull)
      list += JField("filters", Extraction.decompose(route.filters))

      route match {
        case r: DeployedRoute ⇒ list += JField("instances", Extraction.decompose(r.targets))
        case _                ⇒
      }

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
