package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BreedTraitValueValidator

import scala.language.postfixOps

object BreedReader extends YamlReader[Breed] with ReferenceYamlReader[Breed] with TraitReader with BreedTraitValueValidator {

  override def readReference: PartialFunction[Any, Breed] = {
    case reference: String ⇒ BreedReference(reference)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (isReference) BreedReference(reference) else read(yaml)
  }

  override protected def expand(implicit source: YamlSourceReader) = {
    <<?[YamlSourceReader]("dependencies") match {
      case None ⇒
      case Some(yaml) ⇒ yaml.pull().map {
        case (alias: String, dependency: Any) ⇒ dependency match {
          case reference: String ⇒ >>("dependencies" :: alias :: "breed" :: "reference", dependency)
          case yaml: YamlSourceReader ⇒ yaml.find[Any]("breed") match {
            case None ⇒
              >>("dependencies" :: alias, None)
              >>("dependencies" :: alias :: "breed", dependency)
            case Some(breed) ⇒
          }
        }
      }
    }

    super.expand
  }

  override protected def parse(implicit source: YamlSourceReader): Breed = {

    val deployable = Deployable(<<![String]("deployable"))

    val dependencies = <<?[YamlSourceReader]("dependencies") match {
      case None ⇒ Map[String, Breed]()
      case Some(yaml) ⇒ yaml.pull().map {
        case (alias: String, dependency: YamlSourceReader) ⇒
          (alias, readReference(dependency.find[Any]("breed").get))
      }
    }

    DefaultBreed(name, deployable, ports(), environmentVariables(), constants(), dependencies)
  }

  override protected def validate(any: Breed): Breed = any match {
    case breed: BreedReference ⇒ breed
    case breed: DefaultBreed ⇒

      breed.traits.foreach(t ⇒ validateStrictName(t.name))

      breed.ports.find(_.value.isEmpty).flatMap(port ⇒ throwException(MissingPortValueError(breed, port)))
      breed.constants.find(_.value.isEmpty).flatMap(constant ⇒ throwException(MissingConstantValueError(breed, constant)))

      validateBreedTraitValues(breed)
      validateNonRecursiveDependencies(breed)

      breed
  }

  def validateNonRecursiveDependencies(breed: Breed): Unit = {

    recursive(breed, Set(breed.name))

    def recursive(breed: Breed, visited: Set[String]): Unit = breed match {
      case db: DefaultBreed ⇒ db.dependencies.foreach { dependency ⇒
        if (visited.contains(dependency._2.name))
          throwException(RecursiveDependenciesError(breed))
        else
          recursive(dependency._2, visited + dependency._2.name)
      }
      case _ ⇒
    }
  }
}
