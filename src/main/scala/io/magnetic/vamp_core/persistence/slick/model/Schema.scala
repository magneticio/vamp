package io.magnetic.vamp_core.persistence.slick.model


import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType

import scala.slick.driver.JdbcProfile
import scala.slick.jdbc.JdbcBackend.Session
import scala.slick.lifted.ForeignKeyQuery

object DependencyType extends Enumeration {
  val Breed = Value
}

object PortType extends Enumeration {
  type PortType = Value
  val HTTP, TCP = Value
}

class Schema(val driver: JdbcProfile, implicit val session: Session) extends Extensions with DriverComponent {
  this: DriverComponent =>

  import driver.simple._
  import io.magnetic.vamp_core.persistence.slick.model.Implicits._

  def createDatabase() =
    (environmentVariableQuery.schema ++ portsQuery.schema ++ breedsQuery.schema ++ dependenciesQuery.schema).create

  case class EnvironmentVariableModel(id: Option[Int],name: String, alias: Option[String], value: Option[String], direction: Trait.Direction.Value, breedName: String) {
    def toVamp = EnvironmentVariable(name, alias, value, direction)
  }

  object EnvironmentVariableModel extends ((Option[Int],String, Option[String], Option[String], Trait.Direction.Value, String) => EnvironmentVariableModel) {
    def fromVamp(env: EnvironmentVariable, breedName: String) = EnvironmentVariableModel(None,env.name.value, env.alias, env.value, env.direction, breedName)
  }

  case class PortModel(id: Option[Int] ,name: String, alias: Option[String], portType: PortType, value: Option[Int], direction: Trait.Direction.Value, breedName: String) {
    def toVamp = portType match {
      case PortType.HTTP => HttpPort(name, alias, value, direction)
      case PortType.TCP => TcpPort(name, alias, value, direction)
      case _ => throw new RuntimeException(s"Handler for this portType: $portType is not implemented")
    }
  }

  object PortModel extends ((Option[Int],String, Option[String], PortType, Option[Int], Trait.Direction.Value, String) => PortModel) {
    def fromVamp(port: Port, breedName: String) =
      port match {
        case TcpPort(_, _, _, _) => PortModel(None,port.name.value, port.alias, PortType.TCP, port.value, port.direction, breedName)
        case HttpPort(_, _, _, _) => PortModel(None,port.name.value, port.alias, PortType.HTTP, port.value, port.direction, breedName)
        case _ => throw new RuntimeException(s"Handler for portType not implemented")
      }
  }

  case class DependencyModel(id: Option[Int],name: String, alias: String, onType: DependencyType.Value, breedName: String) {
    def toVamp = onType match {
      case DependencyType.Breed => alias -> BreedReference(name)
      case _ => throw new RuntimeException(s"Handler for this dependencyType: $onType is not implemented")
    }
  }

  object DependencyModel extends ((Option[Int],String, String, DependencyType.Value, String) => DependencyModel) {
    def fromVamp(dependency: Breed, alias: String, breedName: String) = DependencyModel(None,dependency.name, alias, DependencyType.Breed, breedName)
  }

  case class BreedModel(name: String, deployableName: String) {
    private def portsQ = portsQuery.filter(p=> p.breedName === name).sortBy(_.id)

    private def environmentVarsQ = environmentVariableQuery.filter(e=> e.breedName === name).sortBy(_.id)

    private def dependencyQ = dependenciesQuery.filter(d=> d.breedName === name && d.onType === DependencyType.Breed).sortBy(_.id)

    def toVamp : Breed = {
      DefaultBreed(name, Deployable(deployableName), portsQ.list.map((p) => p.toVamp), environmentVarsQ.list.map((e) => e.toVamp), dependencyQ.list.map((d) => d.toVamp).toMap)
    }
  }

  object BreedModel extends ((String, String) => BreedModel) {
    def fromVamp(breed: DefaultBreed) = BreedModel(name = breed.name, deployableName = breed.deployable.name)
  }

  class Dependencies(tag: Tag) extends Table[DependencyModel](tag, "dependencies") {
    def id = column[Int]("dep_id", O.AutoInc, O.PrimaryKey)
    def name = column[String]("name")
    def alias = column[String]("alias")
    def onType = column[DependencyType.Value]("on_type")
    def breedName = column[String]("breed_name")
    def * = (id.?, name, alias, onType, breedName) <>(DependencyModel.tupled, DependencyModel.unapply)

    def idx = index("idx_dependencies", (name, alias, onType, breedName), unique = true)

    def breed : ForeignKeyQuery[Breeds, BreedModel] =
      foreignKey("dep_breed_fk", breedName, TableQuery[Breeds])(_.name )
  }

  val dependenciesQuery = TableQuery[Dependencies]

  class Ports(tag: Tag) extends Table[PortModel](tag, "ports") {
    def id = column[Int]("port_id", O.AutoInc, O.PrimaryKey)
    def name = column[String]("name")
    def alias = column[Option[String]]("alias")
    def portType = column[PortType]("port_type")
    def value = column[Option[Int]]("value")
    def direction = column[Trait.Direction.Value]("direction")
    def breedName = column[String]("breed_name")

    def * = (id.?, name, alias, portType, value, direction, breedName) <>(PortModel.tupled, PortModel.unapply)

    def idx = index("idx_ports", (breedName, name), unique = true)

    def breed : ForeignKeyQuery[Breeds, BreedModel] =
      foreignKey("port_breed_fk", breedName, TableQuery[Breeds])(_.name )
  }

  val portsQuery = TableQuery[Ports]

  class EnvironmentVariables(tag: Tag) extends Table[EnvironmentVariableModel](tag, "environment_vars") {
    def id = column[Int]("env_id", O.AutoInc, O.PrimaryKey)
    def name = column[String]("name")
    def alias = column[Option[String]]("alias")
    def value = column[Option[String]]("value")
    def direction = column[Trait.Direction.Value]("direction")
    def breedName = column[String]("breed_name")

    def * = (id.?, name, alias, value, direction, breedName) <>(EnvironmentVariableModel.tupled, EnvironmentVariableModel.unapply)

    def idx = index("idx_environment_vars", (breedName, name), unique = true)

    def breed : ForeignKeyQuery[Breeds, BreedModel] =
      foreignKey("env_breed_fk", breedName, TableQuery[Breeds])(_.name )
  }

  val environmentVariableQuery = TableQuery[EnvironmentVariables]

  class Breeds(tag: Tag) extends Table[BreedModel](tag, "breeds") {
    def id = column[Int]("breed_id", O.AutoInc)
    def name = column[String]("name", O.PrimaryKey)
    def deployableName = column[String]("deployable_name")

    def * = (name, deployableName) <>(BreedModel.tupled, BreedModel.unapply)
  }

  val breedsQuery = TableQuery[Breeds]


}

