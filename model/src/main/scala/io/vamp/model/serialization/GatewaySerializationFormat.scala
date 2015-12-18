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
        list += JField("port", JString(gateway.port.value.get))
      }

      list += JField("sticky", Extraction.decompose(gateway.sticky))
      list += JField("routes", Extraction.decompose {
        gateway.routes.map { route ⇒
          route.path.source -> route
        } toMap
      })
      list += JField("active", JBool(gateway.active))

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
