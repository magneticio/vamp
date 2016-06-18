package io.vamp.model.artifact

import io.vamp.model.reader.Percentage

import scala.language.implicitConversions

object Gateway {

  val anonymous = ""

  object Sticky extends Enumeration {

    val Route, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = Gateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

  def inner(name: String) = GatewayPath(name).segments.size == 3
}

case class GatewayService(host: String, port: Port)

case class Gateway(name: String,
                   port: Port,
                   service: Option[GatewayService],
                   sticky: Option[Gateway.Sticky.Value],
                   virtualHosts: List[String],
                   routes: List[Route],
                   deployed: Boolean = false) extends Artifact with Lookup {

  def routeBy(path: GatewayPath) = routes.find(_.path == path)

  def defaultBalance = if (port.`type` == Port.Type.Http) "roundrobin" else "leastconn"

  def inner = Gateway.inner(name)

  def hasRouteTargets = routes.exists {
    case r: DefaultRoute ⇒ r.targets.nonEmpty
    case r               ⇒ false
  }

  def domain(root: String): String = {
    (GatewayPath(name).segments.reverse ++ root.split('.').toList).map(_.trim).filterNot(_.isEmpty).map({ domain ⇒
      if (domain.matches("^[\\d\\p{L}].*$")) domain.replaceAll("[^\\p{L}\\d]", "-") else domain
    }).mkString(".")
  }
}

object GatewayPath {

  val pathDelimiter = "/"

  private val pathDelimiterSplitter = "\\/"

  def apply(source: String) = string2path(source)

  def apply(path: List[Any] = Nil) = list2path(path.map(_.toString))

  def external(source: String) = source.startsWith("[") && source.endsWith("]")

  implicit def string2path(source: String): GatewayPath = {
    if (external(source))
      new GatewayPath(source, source :: Nil)
    else
      new GatewayPath(source, source.split(pathDelimiterSplitter).toList.filterNot(_.isEmpty))
  }

  implicit def list2path(path: List[String]): GatewayPath = new GatewayPath(path.mkString(pathDelimiter), path.filterNot(_.isEmpty))
}

case class GatewayPath(source: String, segments: List[String]) {

  val normalized = segments.mkString(GatewayPath.pathDelimiter)

  override def equals(obj: scala.Any): Boolean = obj match {
    case routePath: GatewayPath ⇒ segments == routePath.segments
    case _                      ⇒ super.equals(obj)
  }

  def external: Option[String] = if (GatewayPath.external(source) && segments.size == 1) Some(source.substring(1, source.length - 1)) else None
}

object Route {
  val noPath = GatewayPath()
}

sealed trait Route extends Artifact {

  def path: GatewayPath

  val length = path.segments.size
}

case class RouteReference(name: String, path: GatewayPath) extends Reference with Route

object DefaultRoute {
  val defaultBalance = "default"
}

case class DefaultRoute(name: String, path: GatewayPath, weight: Option[Percentage], filterStrength: Option[Percentage], filters: List[Filter], rewrites: List[Rewrite], balance: Option[String], targets: List[RouteTarget] = Nil) extends Route {

  def hasRoutingFilters: Boolean = filters.exists(_.isInstanceOf[DefaultFilter])

  def external = path.external.isDefined
}

object InternalRouteTarget {

  def apply(name: String, port: Int) = new InternalRouteTarget(name, None, port)
}

sealed trait RouteTarget extends Artifact with Lookup

case class InternalRouteTarget(name: String, host: Option[String], port: Int) extends RouteTarget

case class ExternalRouteTarget(url: String) extends RouteTarget {
  val name = url
}

sealed trait Filter extends Artifact

case class FilterReference(name: String) extends Reference with Filter

case class DefaultFilter(name: String, condition: String) extends Filter

sealed trait Rewrite extends Artifact

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
}
