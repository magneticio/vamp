package io.magnetic.vamp_core.persistence.slick.operations.actor

import akka.actor.Props
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.persistence.common.operations.message.Messages._
import io.magnetic.vamp_core.persistence.slick.model.{DependencyType, Schema}


class BreedActor(schema: Schema) extends DbActor(schema) {

  import io.magnetic.vamp_core.persistence.slick.model.Implicits._
  import schema._
  import schema.driver.simple._

  override def dbOperations(msg: Any): Receive = {
    case SaveBreed(breed: DefaultBreed) =>
      deleteBreed(breed.name)
      insertBreed(breed)
      sender ! getBreed(breed.name).get

    case GetBreed(name: String) =>
      getBreed(name) match {
        case Some(breed) => sender ! breed
        case None => sender ! NotFound
      }

    case DeleteBreed(name: String) =>
      deleteBreed(name) match {
        case 0 => sender ! NotFound
        case _ => sender ! OperationSuccess
      }

    case ListBreeds(pageNumber: Int, perPage: Int) =>
      sender ! getBreeds(pageNumber, perPage)

  }

  private def deleteBreed(name: String): Int = {
    val query = BreedModel.breedsQuery.filter((b) => b.name === name)
    query.firstOption match {
      case None => 0
      case _ =>
        PortModel.portsQuery.filter((p) => p.breedName === name).delete
        EnvironmentVariableModel.environmentVariableQuery.filter((e) => e.breedName === name).delete
        DependencyModel.dependenciesQuery.filter((d) => d.breedName === name && d.onType === DependencyType.Breed).delete
        query.delete
    }
  }

  private def insertBreed(breed: DefaultBreed): DefaultBreed = {
    BreedModel.breedsQuery += breed //BreedModel.fromVamp(breed)
    PortModel.portsQuery ++= breed.ports.map(port => PortModel.toPortModel(port, breed.name))
    DependencyModel.dependenciesQuery ++= breed.dependencies.map(dep => DependencyModel.toDependencyModel(dep._2, dep._1, breed.name))
    EnvironmentVariableModel.environmentVariableQuery ++= breed.environmentVariables.map(env => EnvironmentVariableModel.toEnvironmentVariableModel(env, breed.name))
    breed
  }

  private def getBreed(breedName: String): Option[Breed] =
    BreedModel.breedsQuery.filter((b) => b.name === breedName).firstOption match {
      case Some(breed) => Some(breed.toBreed)
      case None => None
    }


  private def getBreeds(pageNumber: Int, perPage: Int): List[Breed] =
    BreedModel.breedsQuery.page(pageNumber, perPage).list.map(breed => breed.toBreed)

}

object BreedActor {
  def props(schema: Schema): Props = Props(new BreedActor(schema))
}
