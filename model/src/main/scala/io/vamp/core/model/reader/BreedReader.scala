package io.vamp.core.model.reader

import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._
import io.vamp.core.model.validator.BreedTraitValueValidator

import scala.language.postfixOps

object BreedReader extends YamlReader[Breed] with ReferenceYamlReader[Breed] with TraitReader with BreedTraitValueValidator {

  override def readReference(any: Any): Breed = any match {
    case reference: String => BreedReference(reference)
    case map: collection.Map[_, _] =>
      implicit val source = map.asInstanceOf[YamlObject]
      if (isReference) BreedReference(reference) else read(map.asInstanceOf[YamlObject])
  }

  override protected def expand(implicit source: YamlObject) = {
    <<?[YamlObject]("dependencies") match {
      case None =>
      case Some(map) => map.map {
        case (alias: String, dependency: Any) => dependency match {
          case reference: String => >>("dependencies" :: alias :: "breed" :: "reference", dependency)
          case map: collection.Map[_, _] => map.asInstanceOf[YamlObject].get("breed") match {
            case None => >>("dependencies" :: alias :: "breed", dependency)
            case Some(breed) =>
          }
        }
      }
    }

    super.expand
  }

  override protected def parse(implicit source: YamlObject): Breed = {

    val deployable = Deployable(<<![String]("deployable"))

    val dependencies = <<?[YamlObject]("dependencies") match {
      case None => Map[String, Breed]()
      case Some(map) => map.map {
        case (alias: String, dependency: collection.Map[_, _]) =>
          (alias, readReference(dependency.asInstanceOf[YamlObject].get("breed").get))
      } toMap
    }

    DefaultBreed(name, deployable, ports(), environmentVariables(), constants(), dependencies)
  }

  override protected def validate(any: Breed): Breed = any match {
    case breed: BreedReference => breed
    case breed: DefaultBreed =>

      breed.ports.find(_.value.isEmpty).flatMap(port => throwException(MissingPortValueError(breed, port)))
      breed.constants.find(_.value.isEmpty).flatMap(constant => throwException(MissingConstantValueError(breed, constant)))

      validateBreedTraitValues(breed)
      validateNonRecursiveDependencies(breed)

      breed
  }

  def validateNonRecursiveDependencies(breed: Breed): Unit = {

    recursive(breed, Set(breed.name))

    def recursive(breed: Breed, visited: Set[String]): Unit = breed match {
      case db: DefaultBreed => db.dependencies.foreach { dependency =>
        if (visited.contains(dependency._2.name))
          throwException(RecursiveDependenciesError(breed))
        else
          recursive(dependency._2, visited + dependency._2.name)
      }
      case _ =>
    }
  }
}
