package io.vamp.model.artifact

import scala.language.implicitConversions

object Gateway {

  val anonymous = ""

  object Sticky extends Enumeration {
    val Service, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = Gateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

}

case class Gateway(name: String, port: Port, sticky: Option[Gateway.Sticky.Value], routes: List[Route]) extends Artifact {
  def routeBy(path: GatewayPath) = routes.find(_.path == path)
}

object GatewayPath {

  val pathDelimiter = "/"

  def apply(source: String) = string2path(source)

  def apply(path: List[Any] = Nil) = list2path(path.map(_.toString))

  implicit def string2path(source: String): GatewayPath = new GatewayPath(source, source.split(pathDelimiter.replaceAllLiterally("/", "\\/")).toList)

  implicit def list2path(path: List[String]): GatewayPath = new GatewayPath(path.mkString(pathDelimiter), path)
}

case class GatewayPath(source: String, path: List[String]) {

  val normalized = path.mkString(GatewayPath.pathDelimiter)

  override def equals(obj: scala.Any): Boolean = obj match {
    case routePath: GatewayPath ⇒ path == routePath.path
    case _                      ⇒ super.equals(obj)
  }
}

object Route {
  val noPath = GatewayPath()
}

sealed trait Route extends Artifact {
  def path: GatewayPath
}

sealed trait AbstractRoute extends Route {

  def weight: Option[Int]

  def filters: List[Filter]
}

case class RouteReference(name: String, path: GatewayPath) extends Reference with Route

case class DefaultRoute(name: String, path: GatewayPath, weight: Option[Int], filters: List[Filter]) extends AbstractRoute

sealed trait ActiveRoute

case class GatewayReferenceRoute(name: String, path: GatewayPath, weight: Option[Int], filters: List[Filter]) extends AbstractRoute with ActiveRoute {
  val reference = path.normalized
}

case class DeploymentGatewayRoute(name: String, path: GatewayPath, weight: Option[Int], filters: List[Filter], instances: List[DeploymentGatewayRouteInstance]) extends AbstractRoute with ActiveRoute

case class DeploymentGatewayRouteInstance(name: String, host: String, port: Int) extends Artifact

sealed trait Filter extends Artifact

case class FilterReference(name: String) extends Reference with Filter

object DefaultFilter {

  val userAgent = "^[uU]ser[-.][aA]gent[ ]?([!])?=[ ]?([a-zA-Z0-9]+)$".r
  val host = "^[hH]ost[ ]?([!])?=[ ]?([a-zA-Z0-9.]+)$".r
  val cookieContains = "^[cC]ookie (.*) [Cc]ontains (.*)$".r
  val hasCookie = "^[Hh]as [Cc]ookie (.*)$".r
  val missesCookie = "^[Mm]isses [Cc]ookie (.*)$".r
  val headerContains = "^[Hh]eader (.*) [Cc]ontains (.*)$".r
  val hasHeader = "^[Hh]as [Hh]eader (.*)$".r
  val missesHeader = "^[Mm]isses [Hh]eader (.*)$".r

  def isHttp(filter: Filter): Boolean = filter match {
    case f: DefaultFilter ⇒ f.condition match {
      case userAgent(n, c)        ⇒ true
      case host(n, c)             ⇒ true
      case cookieContains(c1, c2) ⇒ true
      case hasCookie(c)           ⇒ true
      case missesCookie(c)        ⇒ true
      case headerContains(h, c)   ⇒ true
      case hasHeader(h)           ⇒ true
      case missesHeader(h)        ⇒ true
      case any                    ⇒ false
    }
    case _ ⇒ false
  }
}

case class DefaultFilter(name: String, condition: String) extends Filter
