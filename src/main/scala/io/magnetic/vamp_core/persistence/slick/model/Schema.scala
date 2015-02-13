package io.magnetic.vamp_core.persistence.slick.model

import io.magnetic.vamp_core.model.{Deployable, Breed, BreedDependency, Trait}

import scala.slick.direct.AnnotationMapper.column
import scala.slick.driver.JdbcProfile
import scala.slick.lifted.Tag
import scala.slick.jdbc.JdbcBackend.Session

object DependencyType extends Enumeration {
  val Breed = Value
}

class Schema(val driver: JdbcProfile, implicit val session: Session) extends Extensions with DriverComponent {
  this: DriverComponent =>
  import driver.simple._
  import Implicits._

  def createDatabase =
    (traitsQuery.schema ++ breedsQuery.schema ++ dependenciesQuery.schema).create


  case class TraitModel(name: String, alias: String, value: String, `type`:  Trait.Type.Value, direction: Trait.Direction.Value, breedName: String) {
    def toVamp = Trait(name, alias, value, `type`, direction)
  }

  object TraitModel extends ((String, String, String, Trait.Type.Value, Trait.Direction.Value, String) => TraitModel) {
    def fromVamp(tr: Trait, breedName: String) = TraitModel(tr.name, tr.alias, tr.value, tr.`type`, tr.direction, breedName)
  }

  case class DependencyModel(name: String, onType: DependencyType.Value, onId: String) {
    def toVamp = onType match {
      case DependencyType.Breed => BreedDependency(name)
      case _ => throw new RuntimeException(s"Handler for this dependencyType: $onType is not implemented")
    }


  }

  object DependencyModel extends ((String, DependencyType.Value, String) => DependencyModel) {
    def fromVamp(dependency: BreedDependency, breedName: String) = DependencyModel(dependency.name, DependencyType.Breed, breedName)
  }

  case class BreedModel(name: String, deployableName: String) {
    // Queries require implicit db session, therefore they could only be executed
    // in designated places
    def traitQ = {
      for {
        t <- traitsQuery
        if t.breedName === name
      } yield t
    }

    def dependencyQ = {
      for {
        d <- dependenciesQuery
        if d.onId === name && d.onType === DependencyType.Breed
      } yield d
    }

    def toVamp(traitList: Seq[TraitModel], depList: Seq[DependencyModel]) = {
      Breed(name, Deployable(deployableName), traitList.map(_.toVamp).toList, depList.map(_.toVamp).toList)
    }

  }

  object BreedModel extends ((String, String) => BreedModel){
    def fromVamp(breed: Breed) = BreedModel(name = breed.name, deployableName = breed.deployable.name)
  }
  
  class Dependencies(tag: Tag) extends Table[DependencyModel](tag, "dependencies") {
    def name = column[String]("name")
    def onType = column[DependencyType.Value]("on_type")
    def onId   = column[String]("on_id")
    
    def * = (name, onType, onId) <> (DependencyModel.tupled, DependencyModel.unapply)
    
    def idx = index("idx_dependencies", (name, onType, onId), unique = true)
    
  }
  
  val dependenciesQuery = TableQuery[Dependencies]

  class Traits(tag: Tag) extends Table[TraitModel](tag, "traits") {
    def name = column[String]("name")
    def alias = column[String]("alias")
    def value = column[String]("value")
    def `type` = column[Trait.Type.Value]("type")
    def direction = column[Trait.Direction.Value]("direction")
    def breedName = column[String]("breed_name")

    def * = (name, alias, value, `type`, direction, breedName) <> (TraitModel.tupled, TraitModel.unapply)

    def idx = index("idx_primary", (breedName, name), unique = true)

  }

  val traitsQuery = TableQuery[Traits]

  class Breeds(tag: Tag) extends Table[BreedModel](tag, "breeds") {
    def name = column[String]("name", O.PrimaryKey)
    def deployableName = column[String]("deployable_name")
    
    def * = (name, deployableName) <> (BreedModel.tupled, BreedModel.unapply)
  }

  val breedsQuery = TableQuery[Breeds]


}

