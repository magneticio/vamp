package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model._

object BlueprintReader extends YamlReader[Blueprint] {

  override protected def expand(implicit source: YamlObject) = {
    //expand2list("traits" :: "ports")
  }

  override protected def parse(implicit source: YamlObject): Blueprint = {

    val clusters = List[Cluster]()//<<?[YamlSource]("clusters" :: "services") match {
//      case None => Map[String, Cluster]()
//      case Some(map) => map.map({ case (name: String, dependency: String) => (name, new Dependency(dependency))}).toMap
//    }

    val endpoints = Map[String, String]()

    val parameters = Map[String, String]()

    new Blueprint(name, clusters, endpoints, parameters)
  }
}
