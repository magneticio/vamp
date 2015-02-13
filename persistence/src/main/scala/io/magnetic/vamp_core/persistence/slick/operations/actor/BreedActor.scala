package io.magnetic.vamp_core.persistence.slick.operations.actor

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import io.magnetic.vamp_core.persistence.common.operations.message.{Messages, Response}
import Messages._
import io.magnetic.vamp_core.model.Breed
import io.magnetic.persistence.slick.model._
import io.magnetic.vamp_core.persistence.common.operations.message.Messages.{OperationSuccess, NotFound}
import io.magnetic.vamp_core.persistence.common.operations.message.Response
import io.magnetic.vamp_core.persistence.slick.model.{Schema, DependencyType, Implicits}


class BreedActor(schema: Schema) extends DbActor(schema){
  import schema.driver.simple._
  import schema._
  import Implicits._

  private def deleteBreed(name: String) = {
    val query = schema.breedsQuery.filter((b) => b.name === name)

    val option = query.firstOption

    if(option.isEmpty){
      NotFound
    } else {
      query.delete
      traitsQuery.filter((t) => t.breedName === name).delete
      dependenciesQuery.filter((d) => d.onId === name && d.onType === DependencyType.Breed).delete
      OperationSuccess
    }
  }


  override def dbOperations(msg: Any): Receive = {
      case SaveBreed(breed: Breed)   =>
        deleteBreed(breed.name)
        breedsQuery += BreedModel.fromVamp(breed)
        traitsQuery ++= breed.traits.map((tr) => TraitModel.fromVamp(tr, breed.name))
        dependenciesQuery ++= breed.dependencies.map((dep) => DependencyModel.fromVamp(dep, breed.name))
        sender ! breed

      case GetBreed(name: String)    =>
        val option = breedsQuery.filter((b) => b.name === name).firstOption
        if(option.isEmpty){
          sender ! NotFound
        } else {
          val breed = option.get
          val traits = breed.traitQ.list
          val dependencies = breed.dependencyQ.list
          sender ! breed.toVamp(traits, dependencies)
        }


      case DeleteBreed(name: String) =>
        sender ! deleteBreed(name)

      case ListBreeds(pageNumber: Int, perPage: Int) =>
        val breedQuery = for{
          b <- breedsQuery.page(pageNumber, perPage)
        } yield b


        val breedList = breedQuery.list.map((breed) => {
          val traitList = breed.traitQ.list
          val depList = breed.dependencyQ.list
          breed.toVamp(traitList, depList)
        })

        sender ! breedList
  }


}

object BreedActor {
  def props(schema: Schema): Props = Props(new BreedActor(schema))
}
