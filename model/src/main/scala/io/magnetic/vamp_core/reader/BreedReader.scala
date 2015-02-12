package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model.{Breed, Dependency, Deployable, Trait}

object BreedReader extends ArtifactReader[Breed] {

  override protected def expand(input: Map[Any, Any]): Map[Any, Any] = {
    expand2list(input, "traits/ports")
    expand2list(input, "traits/environment_variables")
  }

  override protected def read(source: Map[Any, Any]): Breed = {
    implicit val input = source
    
    val deployable = new Deployable(getOrError[String]("deployable"))

    val ports = get[List[_]]("traits/ports") match {
      case None => List[Trait]()
      case Some(list) => list.map { port => `trait`(asMap[Any, Any](port), Trait.Type.Port)}
    }

    val environmentVariables = get[List[_]]("traits/environment_variables") match {
      case None => List[Trait]()
      case Some(list) => list.map { ev => `trait`(asMap[Any, Any](ev), Trait.Type.EnvironmentVariable)}
    }

    val dependencies = get[Map[_, _]]("dependencies") match {
      case None => Map[String, Dependency]()
      case Some(map) => map.map { case (name: String, dependency: String) => (name, new Dependency(dependency))}
    }

    new Breed(name, deployable, ports ++ environmentVariables, dependencies)
  }

  private def `trait`(template: Map[Any, Any], `type`: Trait.Type.Value): Trait = {
    implicit val input = template
    new Trait(name, get[String]("alias"), get[String]("value"), `type`, Trait.Direction.withName(getOrError[String]("direction").toLowerCase.capitalize))
  }
}
