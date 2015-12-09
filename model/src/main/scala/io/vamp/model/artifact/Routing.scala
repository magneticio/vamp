package io.vamp.model.artifact

object Routing {

  object Sticky extends Enumeration {
    val Service, Instance = Value
  }

}

case class Routing(sticky: Option[Routing.Sticky.Value], routes: Map[String, Route])

trait Route extends Artifact

case class RouteReference(name: String) extends Reference with Route

case class DefaultRoute(name: String, weight: Option[Int], filters: List[Filter]) extends Route

trait Filter extends Artifact

case class FilterReference(name: String) extends Reference with Filter

case class DefaultFilter(name: String, condition: String) extends Filter
