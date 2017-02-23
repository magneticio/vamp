package io.vamp.model.reader

import io.vamp.common.config.Config
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.validator.BreedTraitValueValidator

object BreedReader extends YamlReader[Breed] with ReferenceYamlReader[Breed] with TraitReader with ArgumentReader with BreedTraitValueValidator {

  private def defaultDeployableType = {
    val path = "vamp.model.default-deployable-type"
    if (Config.has(path)()) Config.string(path)() else Deployable.defaultType
  }

  override def readReference: PartialFunction[Any, Breed] = {
    case reference: String ⇒ BreedReference(reference)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (isReference) BreedReference(reference) else read(yaml)
  }

  override protected def expand(implicit source: YamlSourceReader) = {

    <<?[Any]("deployable") match {
      case Some(str: String) ⇒ >>("deployable", YamlSourceReader("definition" → str))
      case _                 ⇒
    }

    <<?[YamlSourceReader]("dependencies") match {
      case None ⇒
      case Some(yaml) ⇒ yaml.pull().map {
        case (alias: String, dependency: Any) ⇒ dependency match {
          case _: String ⇒ >>("dependencies" :: alias :: "breed" :: "reference", dependency)
          case yaml: YamlSourceReader ⇒ yaml.find[Any]("breed") match {
            case None ⇒
              >>("dependencies" :: alias, None)
              >>("dependencies" :: alias :: "breed", dependency)
            case _ ⇒
          }
        }
      }
    }

    expandArguments()

    super.expand
  }

  override protected def parse(implicit source: YamlSourceReader): Breed = {

    val deployable = <<?[String]("deployable" :: "type" :: Nil) match {
      case Some(t) ⇒ Deployable(t, <<![String]("deployable" :: "definition" :: Nil))
      case None    ⇒ Deployable(defaultDeployableType, <<![String]("deployable" :: "definition" :: Nil))
    }

    val dependencies = <<?[YamlSourceReader]("dependencies") match {
      case None ⇒ Map[String, Breed]()
      case Some(yaml) ⇒ yaml.pull().collect {
        case (alias: String, dependency: YamlSourceReader) ⇒
          (alias, readReference(dependency.find[Any]("breed").get))
      }
    }

    DefaultBreed(name, metadata, deployable, ports(), environmentVariables(), constants(), arguments(), dependencies, HealthCheckReader.read)
  }

  override protected def validate(any: Breed): Breed = any match {
    case breed: BreedReference ⇒ breed
    case breed: DefaultBreed ⇒

      breed.traits.foreach(t ⇒ validateStrictName(t.name))

      breed.ports.find(_.value.isEmpty).flatMap(port ⇒ throwException(MissingPortValueError(breed, port)))
      breed.constants.find(_.value.isEmpty).flatMap(constant ⇒ throwException(MissingConstantValueError(breed, constant)))

      validateArguments(breed.arguments)

      validateHealthCheck(breed)
      validateBreedTraitValues(breed)
      validateNonRecursiveDependencies(breed)

      breed
  }

  private def validateHealthCheck(any: Breed): Unit = any match {
    case breed: DefaultBreed ⇒
      breed.healthChecks.foreach {
        _.foreach { healthCheck ⇒
          if (!breed.ports.exists(_.name == healthCheck.port)) throwException(UnresolvedPortReferenceError(healthCheck.port))
          if (healthCheck.failures < 0) throwException(NegativeFailuresNumberError(healthCheck.failures))
        }
      }
    case _ ⇒
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
