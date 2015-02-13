package io.magnetic.vamp_core.reader

import io.magnetic.vamp_core.model._

object BreedReader extends YamlReader[Breed] {

  override protected def expand(implicit source: YamlObject) = {
    expand2list("traits" :: "ports")
    expand2list("traits" :: "environment_variables")
    expandDependencies
  }

  private def expandDependencies(implicit source: YamlObject) = {
    <<?[YamlObject]("dependencies") match {
      case None =>
      case Some(map) => map.map({
        case (name: String, dependency: Any) =>
          val expanded = dependency match {
            case reference: String => new YamlObject() += ("breed" -> (new YamlObject() += ("name" -> reference)))
            case map: collection.Map[_, _] =>
              implicit val source = map.asInstanceOf[YamlObject]
              <<?[Any]("breed") match {
                case None => new YamlObject() += ("breed" -> source)
                case Some(reference: String) => new YamlObject() += ("breed" -> (new YamlObject() += ("name" -> reference)))
                case Some(_) => source
              }
          }
          >>("dependencies" :: name, expanded)
      })
    }
  }

  override protected def parse(implicit source: YamlObject): Breed = {

    val deployable = new Deployable(<<![String]("deployable"))

    val ports = <<?[YamlList]("traits" :: "ports") match {
      case None => List[Trait]()
      case Some(list: YamlList) => list.map { port => `trait`(port, Trait.Type.Port)}
    }

    val environmentVariables = <<?[YamlList]("traits" :: "environment_variables") match {
      case None => List[Trait]()
      case Some(list: YamlList) => list.map { ev => `trait`(ev, Trait.Type.EnvironmentVariable)}
    }

    val dependencies = <<?[YamlObject]("dependencies") match {
      case None => Map[String, Breed]()
      case Some(map) => map.map({
        case (alias: String, dependency: collection.Map[_, _]) =>
          implicit val source = dependency.asInstanceOf[YamlObject].get("breed").get.asInstanceOf[YamlObject]
          <<?[String]("deployable") match {
            case None => (alias, new BreedReference(name))
            case Some(_) => (alias, read)
          }
      }).toMap
    }

    new DefaultBreed(name, deployable, ports ++ environmentVariables, dependencies)
  }

  private def `trait`(template: YamlObject, `type`: Trait.Type.Value): Trait = {
    implicit val input = template
    new Trait(name, <<?[String]("alias"), <<?[String]("value"), `type`, Trait.Direction.withName(<<![String]("direction").toLowerCase.capitalize))
  }
}
