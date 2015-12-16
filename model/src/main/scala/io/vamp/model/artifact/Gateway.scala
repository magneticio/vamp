package io.vamp.model.artifact

import scala.language.implicitConversions

object AbstractGateway {

  object Sticky extends Enumeration {
    val Service, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = AbstractGateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

}

trait AbstractGateway extends Artifact {

  def sticky: Option[AbstractGateway.Sticky.Value]

  def routes: List[Route]

  def routeBy(path: RoutePath) = routes.find(_.path == path)
}

case class Gateway(name: String, port: Port, sticky: Option[AbstractGateway.Sticky.Value], routes: List[Route]) extends AbstractGateway

object ClusterGateway {
  val anonymous = ""
}

case class ClusterGateway(name: String, port: String, sticky: Option[AbstractGateway.Sticky.Value], routes: List[Route]) extends AbstractGateway

object Route {
  val noPath = RoutePath()
}

trait Route extends Artifact {
  def path: RoutePath
}

object RoutePath {

  def apply(path: List[String] = Nil) = list2path(path)

  implicit def string2path(path: String): RoutePath = new RoutePath(path)

  implicit def list2path(path: List[String]): RoutePath = new RoutePath(path.mkString("/"))
}

case class RoutePath(source: String) {
  val path = source.split("[\\/\\.]").toList

  override def equals(obj: scala.Any): Boolean = obj match {
    case routePath: RoutePath ⇒ path == routePath.path
    case _                    ⇒ super.equals(obj)
  }
}

case class RouteReference(name: String, path: RoutePath) extends Reference with Route

case class DefaultRoute(name: String, path: RoutePath, weight: Option[Int], filters: List[Filter]) extends Route

trait Filter extends Artifact

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
