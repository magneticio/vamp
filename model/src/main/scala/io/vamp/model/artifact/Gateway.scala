package io.vamp.model.artifact

import io.vamp.common.{ Artifact, Lookup, Namespace, Reference }
import io.vamp.model.parser.{ AstNode, RouteSelectorParser }
import io.vamp.model.reader.Percentage

import scala.language.implicitConversions

object Gateway {

  val anonymous = ""

  val kind: String = "gateways"

  object Sticky extends Enumeration {

    val Route, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = Gateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

  def internal(name: String): Boolean = GatewayPath(name).segments.size == 3
}

case class GatewayService(host: String, port: Port)

case class Gateway(
    name:         String,
    metadata:     Map[String, Any],
    port:         Port,
    service:      Option[GatewayService],
    sticky:       Option[Gateway.Sticky.Value],
    virtualHosts: List[String],
    selector:     Option[RouteSelector],
    routes:       List[Route],
    deployed:     Boolean                      = false
) extends Artifact with Lookup {

  val kind: String = Gateway.kind

  def routeBy(path: GatewayPath): Option[Route] = routes.find(_.path == path)

  def defaultBalance: String = if (port.`type` == Port.Type.Http) "roundrobin" else "leastconn"

  def internal: Boolean = Gateway.internal(name)

  def hasRouteTargets: Boolean = routes.exists {
    case r: DefaultRoute ⇒ r.targets.nonEmpty
    case _               ⇒ false
  }
}

object GatewayPath {

  val pathDelimiter = "/"

  private val pathDelimiterSplitter = "\\/"

  def apply(source: String): GatewayPath = string2path(source)

  def apply(path: List[Any] = Nil): GatewayPath = list2path(path.map(_.toString))

  def external(source: String): Boolean = source.startsWith("[") && source.endsWith("]")

  implicit def string2path(source: String): GatewayPath = {
    if (external(source))
      new GatewayPath(source, source :: Nil)
    else
      new GatewayPath(source, source.split(pathDelimiterSplitter).toList.filterNot(_.isEmpty))
  }

  implicit def list2path(path: List[String]): GatewayPath = new GatewayPath(path.mkString(pathDelimiter), path.filterNot(_.isEmpty))
}

case class GatewayPath(source: String, segments: List[String]) {

  val normalized: String = segments.mkString(GatewayPath.pathDelimiter)

  override def equals(obj: scala.Any): Boolean = obj match {
    case routePath: GatewayPath ⇒ segments == routePath.segments
    case _                      ⇒ super.equals(obj)
  }

  def external: Option[String] = if (GatewayPath.external(source) && segments.size == 1) Some(source.substring(1, source.length - 1)) else None
}

object Route {
  val noPath = GatewayPath()

  val kind: String = "routes"
}

sealed trait Route extends Artifact with Lookup {

  val kind: String = Route.kind

  def path: GatewayPath

  val length: Int = path.segments.size
}

case class RouteReference(name: String, path: GatewayPath) extends Reference with Route

object DefaultRoute {
  val defaultBalance = "default"
}

case class DefaultRoute(
    name:              String,
    metadata:          Map[String, Any],
    path:              GatewayPath,
    selector:          Option[RouteSelector],
    weight:            Option[Percentage],
    condition:         Option[Condition],
    conditionStrength: Option[Percentage],
    rewrites:          List[Rewrite],
    balance:           Option[String],
    targets:           List[RouteTarget]     = Nil
) extends Route {

  def definedCondition: Boolean = condition.isDefined && condition.forall(_.isInstanceOf[DefaultCondition])

  def external: Boolean = path.external.isDefined
}

object InternalRouteTarget {

  def apply(name: String, port: Int) = new InternalRouteTarget(name, None, port)
}

sealed trait RouteTarget extends Artifact with Lookup {
  val kind: String = "targets"
}

case class InternalRouteTarget(name: String, host: Option[String], port: Int) extends RouteTarget {
  val metadata = Map()
}

case class ExternalRouteTarget(url: String, metadata: Map[String, Any] = Map()) extends RouteTarget {
  val name: String = url
}

object Condition {
  val kind: String = "conditions"
}

sealed trait Condition extends Artifact {
  val kind: String = Condition.kind
}

case class ConditionReference(name: String) extends Reference with Condition

case class DefaultCondition(name: String, metadata: Map[String, Any], definition: String) extends Condition

object Rewrite {
  val kind: String = "rewrites"
}

sealed trait Rewrite extends Artifact {
  val kind: String = Rewrite.kind
}

case class RewriteReference(name: String) extends Reference with Rewrite

object PathRewrite {

  private val matcher = "^(?i)(.+) if (.+)$".r

  def parse(name: String, definition: String): Option[PathRewrite] = definition match {
    case matcher(path, condition) ⇒ Option(PathRewrite(name, path, condition))
    case _                        ⇒ None
  }
}

case class PathRewrite(name: String, path: String, condition: String) extends Rewrite {
  val definition = s"$path if $condition"

  val metadata: Map[String, Any] = Map()
}

object GatewayLookup {

  def name(artifact: Lookup, path: List[String] = Nil): String = path match {
    case Nil ⇒ artifact.name
    case _   ⇒ s"${artifact.name}//${path.mkString("/")}"
  }

  def lookup(artifact: Lookup, path: List[String] = Nil)(implicit namespace: Namespace): String = {
    val lookup = path match {
      case Nil ⇒ artifact.lookupName
      case _   ⇒ artifact.lookup(expand(artifact, path))
    }
    if (namespace.name == Namespace.empty.name) lookup else namespace.lookupName + lookup
  }

  private def expand(artifact: Lookup, path: List[String]): String = (path match {
    case Nil ⇒ expand(artifact.name :: Nil)
    case _   ⇒ expand(artifact.name :: Nil) ++ expand(path)
  }).mkString("/")

  private def expand(path: List[String]): List[String] = path match {
    case some if some.size < 4 ⇒ expand(some :+ "*")
    case _                     ⇒ path
  }
}

case class RouteSelector(definition: String) {

  lazy val node: AstNode = new RouteSelectorParser().parse(definition)

  def verified: RouteSelector = {
    node // parse to verify
    this
  }

  override def toString: String = definition
}
