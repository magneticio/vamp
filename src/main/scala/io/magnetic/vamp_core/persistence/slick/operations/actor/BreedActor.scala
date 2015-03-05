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

    // TODO add operations for blueprint


  }

  private def deleteBreed(name: String): Int = {
    val query = schema.breedsQuery.filter((b) => b.name === name)
    query.firstOption match {
      case None => 0
      case _ =>
        portsQuery.filter((p) => p.breedName === name).delete
        environmentVariableQuery.filter((e) => e.breedName === name).delete
        dependenciesQuery.filter((d) => d.breedName === name && d.onType === DependencyType.Breed).delete
        query.delete
    }
  }

  private def insertBreed(breed: DefaultBreed): DefaultBreed = {
    breedsQuery += BreedModel.fromVamp(breed)
    portsQuery ++= breed.ports.map(tr => PortModel.fromVamp(tr, breed.name))
    dependenciesQuery ++= breed.dependencies.map((tuple) => DependencyModel.fromVamp(tuple._2, tuple._1, breed.name))
    environmentVariableQuery ++= breed.environmentVariables.map(env => EnvironmentVariableModel.fromVamp(env, breed.name))
    breed
  }

  private def getBreed(breedName: String): Option[Breed] = {
    breedsQuery.filter((b) => b.name === breedName).firstOption match {
      case Some(breed) => Some(breed.toVamp)
      case None => None
    }
  }

  private def getBreeds(pageNumber: Int, perPage: Int): List[Breed] = {
    val breedQuery = for {
      b <- breedsQuery.page(pageNumber, perPage)
    } yield b
    breedQuery.list.map((breed) => {
      breed.toVamp
    })
  }
}

object BreedActor {
  def props(schema: Schema): Props = Props(new BreedActor(schema))
}
