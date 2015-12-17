package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps

trait AbstractGatewayReader[T <: Gateway] extends YamlReader[T] with AnonymousYamlReader[T] {

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[Any]("routes") match {
      case Some(route: String) ⇒ >>("routes" :: route :: Nil, YamlSourceReader())
      case _                   ⇒
    }
    super.expand
  }

  protected def port(implicit source: YamlSourceReader): Port = <<![Any]("port") match {
    case value: Int    ⇒ Port.portFor(value)
    case value: String ⇒ Port.portFor(value)
    case any           ⇒ throwException(UnexpectedTypeError("port", classOf[String], any.getClass))
  }

  protected def sticky(path: YamlPath)(implicit source: YamlSourceReader) = <<?[String](path) match {
    case Some(sticky) ⇒ if (sticky.toLowerCase == "none") None else Option(Gateway.Sticky.byName(sticky).getOrElse(throwException(IllegalRoutingStickyValue(sticky))))
    case None         ⇒ None
  }

  protected def routes(implicit source: YamlSourceReader): List[Route] = <<?[YamlSourceReader]("routes") match {
    case Some(map) ⇒ map.pull().map {
      case (name: String, _) ⇒ RouteReader.readReferenceOrAnonymous(<<![Any]("routes" :: name :: Nil)) match {
        case route: DefaultRoute   ⇒ route.copy(path = name)
        case route: RouteReference ⇒ route.copy(path = name)
      }
    } toList
    case None ⇒ Nil
  }
}

object GatewayReader extends AbstractGatewayReader[Gateway] {

  override protected def parse(implicit source: YamlSourceReader): Gateway = Gateway(name, port, sticky("sticky"), routes)
}

object ClusterGatewayReader extends AbstractGatewayReader[Gateway] {

  override protected def parse(implicit source: YamlSourceReader): Gateway = Gateway(name, Port(<<![String]("port"), None, None), sticky("sticky"), routes)
}

object RouteReader extends YamlReader[Route] with WeakReferenceYamlReader[Route] {

  import YamlSourceReader._

  override protected def createReference(implicit source: YamlSourceReader): Route = RouteReference(reference, Route.noPath)

  override protected def createDefault(implicit source: YamlSourceReader): Route = DefaultRoute(name, Route.noPath, <<?[Int]("weight"), filters)

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[Any]("filters") match {
      case Some(s: String)     ⇒ expandToList("filters")
      case Some(list: List[_]) ⇒
      case Some(m)             ⇒ >>("filters", List(m))
      case _                   ⇒
    }
    super.expand
  }

  protected def filters(implicit source: YamlSourceReader): List[Filter] = <<?[YamlList]("filters") match {
    case None ⇒ List[Filter]()
    case Some(list: YamlList) ⇒ list.map {
      FilterReader.readReferenceOrAnonymous
    }
  }
}

object FilterReader extends YamlReader[Filter] with WeakReferenceYamlReader[Filter] {

  override protected def createReference(implicit source: YamlSourceReader): Filter = FilterReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Filter = DefaultFilter(name, <<![String]("condition"))
}
