package io.vamp.model.artifact

import io.vamp.model.reader.Percentage

import scala.language.implicitConversions

object Gateway {

  val anonymous = ""

  object Sticky extends Enumeration {

    val Route, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = Gateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

  def internal(name: String) = GatewayPath(name).segments.size == 3
}

case class GatewayService(host: String, port: Port)

case class Gateway(
    name:         String,
    port:         Port,
    service:      Option[GatewayService],
    sticky:       Option[Gateway.Sticky.Value],
    virtualHosts: List[String],
    routes:       List[Route],
    deployed:     Boolean                      = false
) extends Artifact with Lookup {

  val kind = "gateway"

  val proxy: Option[String] = {
    if (deployed && port.`type` == Port.Type.Http && service.nonEmpty) Option(GatewayPath(name).normalized) else None
  }

  def routeBy(path: GatewayPath) = routes.find(_.path == path)

  def defaultBalance = if (port.`type` == Port.Type.Http) "roundrobin" else "leastconn"

  def internal = Gateway.internal(name)

  def hasRouteTargets = routes.exists {
    case r: DefaultRoute ⇒ r.targets.nonEmpty
    case _               ⇒ false
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

sealed trait Route extends Artifact with Lookup {

  val kind = "route"

  def path: GatewayPath

  val length = path.segments.size
}

case class RouteReference(name: String, path: GatewayPath) extends Reference with Route

object DefaultRoute {
  val defaultBalance = "default"
}

case class DefaultRoute(name: String, path: GatewayPath, weight: Option[Percentage], condition: Option[Condition], conditionStrength: Option[Percentage], rewrites: List[Rewrite], balance: Option[String], targets: List[RouteTarget] = Nil) extends Route {

  def definedCondition: Boolean = condition.isDefined && condition.forall(_.isInstanceOf[DefaultCondition])

  def external = path.external.isDefined
}

object InternalRouteTarget {

  def apply(name: String, port: Int) = new InternalRouteTarget(name, None, port)
}

sealed trait RouteTarget extends Artifact with Lookup {
  val kind = "target"
}

case class InternalRouteTarget(name: String, host: Option[String], port: Int) extends RouteTarget

case class ExternalRouteTarget(url: String) extends RouteTarget {
  val name = url
}

sealed trait Condition extends Artifact {
  val kind = "condition"
}

case class ConditionReference(name: String) extends Reference with Condition

case class DefaultCondition(name: String, definition: String) extends Condition

sealed trait Rewrite extends Artifact {
  val kind = "rewrite"
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
}

object GatewayLookup {

  def name(artifact: Lookup, path: List[String] = Nil): String = path match {
    case Nil ⇒ artifact.name
    case _   ⇒ s"${artifact.name}//${path.mkString("/")}"
  }

  def lookup(artifact: Lookup, path: List[String] = Nil): String = path match {
    case Nil ⇒ artifact.lookupName
    case _   ⇒ artifact.lookup(expand(artifact, path))
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
