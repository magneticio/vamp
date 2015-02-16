package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model._
import scala.language.postfixOps

object BreedReader extends YamlReader[Breed] with ReferenceYamlReader[Breed] {

  override def readReference(any: Any): Breed = any match {
    case reference: String => BreedReference(reference)
    case map: collection.Map[_, _] => 
      implicit val source = map.asInstanceOf[YamlObject]
      <<?[Any]("deployable") match {
        case None => BreedReference(name)
        case Some(_) => read(map.asInstanceOf[YamlObject])
      }
  }

  override protected def expand(implicit source: YamlObject) = {
    expandToList("traits" :: "ports")
    expandToList("traits" :: "environment_variables")

    <<?[YamlObject]("dependencies") match {
      case None =>
      case Some(map) => map.map {
        case (alias: String, dependency: Any) => dependency match {
          case reference: String => >>("dependencies" :: alias :: "breed" :: "name", dependency)
          case map: collection.Map[_, _] => map.asInstanceOf[YamlObject].get("breed") match {
            case None => >>("dependencies" :: alias :: "breed", dependency)
            case Some(breed) =>
          }
        }
      }
    }
    
    source
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
      case Some(map) => map.map {
        case (alias: String, dependency: collection.Map[_, _]) =>
          (alias, readReference(dependency.asInstanceOf[YamlObject].get("breed").get))
      } toMap
    }

    DefaultBreed(name, deployable, ports ++ environmentVariables, dependencies)
  }

  private def `trait`(implicit source: YamlObject, `type`: Trait.Type.Value): Trait =
    Trait(name, <<?[String]("alias"), <<?[String]("value"), `type`, Trait.Direction.withName(<<![String]("direction").toLowerCase.capitalize))
}
