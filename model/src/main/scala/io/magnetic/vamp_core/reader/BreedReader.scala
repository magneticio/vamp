package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model.{Breed, Dependency, Deployable, Trait}

object BreedReader extends ArtifactReader[Breed] {

  override protected def expand(implicit input: YamlSource) = {
    expand2list("traits" :: "ports")
    expand2list("traits" :: "environment_variables")
  }

  override protected def parse(implicit input: YamlSource): Breed = {
    
    val deployable = new Deployable(<<![String]("deployable"))

    val ports = <<?[List[_]]("traits" :: "ports") match {
      case None => List[Trait]()
      case Some(list: List[_]) => list.map { port => `trait`(port.asInstanceOf[YamlSource], Trait.Type.Port)}
    }

    val environmentVariables = <<?[List[_]]("traits" :: "environment_variables") match {
      case None => List[Trait]()
      case Some(list: List[_]) => list.map { ev => `trait`(ev.asInstanceOf[YamlSource], Trait.Type.EnvironmentVariable)}
    }

    val dependencies = <<?[collection.Map[_, _]]("dependencies") match {
      case None => Map[String, Dependency]()
      case Some(map) => map.map({ case (name: String, dependency: String) => (name, new Dependency(dependency))}).toMap
    }

    new Breed(name, deployable, ports ++ environmentVariables, dependencies)
  }

  private def `trait`(template: YamlSource, `type`: Trait.Type.Value): Trait = {
    implicit val input = template
    new Trait(name, <<?[String]("alias"), <<?[String]("value"), `type`, Trait.Direction.withName(<<![String]("direction").toLowerCase.capitalize))
  }
}
