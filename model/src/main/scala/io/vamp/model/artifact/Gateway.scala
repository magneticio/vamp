package io.vamp.model.artifact

trait Gateway extends Artifact

object DefaultGateway {

  val anonymous = PortReference("")

  object Sticky extends Enumeration {
    val Service, Instance = Value

    def byName(sticky: String): Option[Sticky.Value] = DefaultGateway.Sticky.values.find(_.toString.toLowerCase == sticky.toLowerCase)
  }

}

case class DefaultGateway(name: String, port: AbstractPort, sticky: Option[DefaultGateway.Sticky.Value], routes: List[Route]) extends Gateway

case class GatewayReference(name: String) extends Gateway with Reference

object Route {
  val noPath = ""
}

trait Route extends Artifact {
  def path: String
}

case class RouteReference(name: String, path: String) extends Reference with Route

case class DefaultRoute(name: String, path: String, weight: Option[Int], filters: List[Filter]) extends Route

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
