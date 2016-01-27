package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._

import scala.language.postfixOps
import scala.util.Try

trait AbstractGatewayReader[T <: Gateway] extends YamlReader[T] with AnonymousYamlReader[T] {

  private val nameMatcher = """^[^\s\[\]]+$""".r

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[Any]("routes") match {
      case Some(route: String) ⇒ >>("routes" :: route :: Nil, YamlSourceReader())
      case Some(routes: List[_]) ⇒ routes.foreach {
        case route: String           ⇒ >>("routes" :: route :: Nil, YamlSourceReader())
        case route: YamlSourceReader ⇒ route.pull().foreach { case (name, value) ⇒ >>("routes" :: name :: Nil, value) }
        case _                       ⇒
      }
      case _ ⇒
    }
    super.expand
  }

  protected def port(implicit source: YamlSourceReader): Port = <<![Any]("port") match {
    case value: Int    ⇒ Port(value)
    case value: String ⇒ Port(value)
    case any           ⇒ throwException(UnexpectedTypeError("port", classOf[String], any.getClass))
  }

  protected def sticky(path: YamlPath)(implicit source: YamlSourceReader) = <<?[String](path) match {
    case Some(sticky) ⇒ if (sticky.toLowerCase == "none") None else Option(Gateway.Sticky.byName(sticky).getOrElse(throwException(IllegalGatewayStickyValue(sticky))))
    case None         ⇒ None
  }

  protected def routes(splitPath: Boolean)(implicit source: YamlSourceReader): List[Route] = <<?[YamlSourceReader]("routes") match {
    case Some(map) ⇒ map.pull().map {
      case (name: String, _) ⇒ routerReader.readReferenceOrAnonymous(<<![Any]("routes" :: name :: Nil)) match {
        case route: DefaultRoute   ⇒ route.copy(path = if (splitPath) name else GatewayPath(name :: Nil))
        case route: RouteReference ⇒ route.copy(path = if (splitPath) name else GatewayPath(name :: Nil))
        case route: DeployedRoute  ⇒ route.copy(path = if (splitPath) name else GatewayPath(name :: Nil))
        case route                 ⇒ route
      }
    } toList
    case None ⇒ Nil
  }

  protected def active(implicit source: YamlSourceReader): Boolean = <<?[Boolean]("active").getOrElse(false)

  override protected def validate(gateway: T): T = {
    gateway.routes.foreach(route ⇒ if (route.length < 1 || route.length > 4) throwException(UnsupportedRoutePathError(route.path)))
    if (gateway.port.`type` != Port.Type.Http && gateway.sticky.isDefined) throwException(StickyPortTypeError(gateway.port.copy(name = gateway.port.value.get)))
    gateway
  }

  override def validateName(name: String): String = {

    def error = throwException(UnsupportedGatewayNameError(name))

    name match {
      case nameMatcher(_*) ⇒ GatewayPath(name).segments match {
        case path ⇒ if (path.size < 1 || path.size > 4) error
      }
      case _ ⇒ error
    }

    name
  }

  protected def routerReader: AbstractRouteReader = RouteReader
}

object GatewayReader extends AbstractGatewayReader[Gateway] {

  override protected def parse(implicit source: YamlSourceReader): Gateway = Gateway(name, port, sticky("sticky"), routes(splitPath = true), active)
}

object ClusterGatewayReader extends AbstractGatewayReader[Gateway] {

  override protected def parse(implicit source: YamlSourceReader): Gateway = Gateway(name, Port(<<![String]("port"), None, None), sticky("sticky"), routes(splitPath = false), active)
}

trait AbstractRouteReader extends YamlReader[Route] with WeakReferenceYamlReader[Route] {

  import YamlSourceReader._

  override protected def createReference(implicit source: YamlSourceReader): Route = RouteReference(reference, Route.noPath)

  override protected def createDefault(implicit source: YamlSourceReader): Route = {
    source.flatten({ entry ⇒ entry == "instances" })
    DefaultRoute(name, Route.noPath, <<?[Percentage]("weight"), filters, rewrites, <<?[String]("balance"))
  }

  override protected def expand(implicit source: YamlSourceReader) = {

    def list(name: String) = <<?[Any](name) match {
      case Some(s: String)     ⇒ expandToList(name)
      case Some(list: List[_]) ⇒
      case Some(m)             ⇒ >>(name, List(m))
      case _                   ⇒
    }

    list("filters")
    list("rewrites")

    super.expand
  }

  protected def filters(implicit source: YamlSourceReader): List[Filter] = <<?[YamlList]("filters") match {
    case None ⇒ List.empty[Filter]
    case Some(list: YamlList) ⇒ list.map {
      FilterReader.readReferenceOrAnonymous
    }
  }

  protected def rewrites(implicit source: YamlSourceReader): List[Rewrite] = <<?[YamlList]("rewrites") match {
    case None ⇒ List.empty[Rewrite]
    case Some(list: YamlList) ⇒ list.map {
      RewriteReader.readReferenceOrAnonymous
    }
  }
}

object RouteReader extends AbstractRouteReader

object DeployedRouteReader extends AbstractRouteReader {

  override protected def createDefault(implicit source: YamlSourceReader): DeployedRoute = {

    val targets = <<?[YamlList]("instances") match {
      case Some(list) ⇒ list.map {
        case yaml ⇒
          implicit val source = yaml
          DeployedRouteTarget(<<![String]("name"), <<![String]("host"), <<![Int]("port"))
      }
      case _ ⇒ Nil
    }

    DeployedRoute(name, Route.noPath, <<?[Percentage]("weight"), filters, rewrites, <<?[String]("balance"), targets)
  }
}

object FilterReader extends YamlReader[Filter] with WeakReferenceYamlReader[Filter] {

  override protected def createReference(implicit source: YamlSourceReader): Filter = FilterReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Filter = DefaultFilter(name, <<![String]("condition"))
}

object RewriteReader extends YamlReader[Rewrite] with WeakReferenceYamlReader[Rewrite] {

  override protected def createReference(implicit source: YamlSourceReader): Rewrite = RewriteReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Rewrite = <<![String]("path") match {
    case definition ⇒
      PathRewrite.parse(name, definition) match {
        case Some(rewrite: PathRewrite) ⇒ rewrite
        case _                          ⇒ throwException(UnsupportedPathRewriteError(definition))
      }
  }
}

trait GatewayMappingReader[T <: Artifact] extends YamlReader[List[T]] {

  import YamlSourceReader._

  def mapping(entry: String)(implicit source: YamlSourceReader): List[T] = <<?[YamlSourceReader](entry) match {
    case Some(yaml) ⇒ read(yaml)
    case None       ⇒ Nil
  }

  protected def parse(implicit source: YamlSourceReader): List[T] = source.pull().keySet.map { key ⇒
    val yaml = <<![YamlSourceReader](key :: Nil)

    <<?[Any](key :: "port") match {
      case Some(value) ⇒ if (!acceptPort) throwException(UnexpectedElement(Map[String, Any](key -> "port"), value.toString))
      case None        ⇒ >>("port", key)(yaml)
    }

    reader.readAnonymous(yaml) match {
      case artifact ⇒ update(key, artifact)
    }

  } toList

  protected def reader: AnonymousYamlReader[T]

  protected def acceptPort: Boolean

  protected def update(key: String, artifact: T)(implicit source: YamlSourceReader): T = artifact
}

object BlueprintGatewayReader extends GatewayMappingReader[Gateway] {

  protected val reader = GatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    source.pull().keySet.map { port ⇒
      <<![Any](port :: Nil) match {
        case route: String ⇒ >>(port :: "routes", route)
        case _             ⇒
      }
    }
    super.expand
  }

  protected def acceptPort = true

  override protected def update(key: String, gateway: Gateway)(implicit source: YamlSourceReader): Gateway =
    gateway.copy(port = gateway.port.copy(name = Try(Port(key).number.toString).getOrElse(key)))
}

object RoutingReader extends GatewayMappingReader[Gateway] {

  protected val reader = ClusterGatewayReader

  override protected def expand(implicit source: YamlSourceReader) = {
    if (source.pull({ entry ⇒ entry == "sticky" || entry == "routes" }).nonEmpty) >>(Gateway.anonymous, <<-())
    super.expand
  }

  protected def acceptPort = false
}
