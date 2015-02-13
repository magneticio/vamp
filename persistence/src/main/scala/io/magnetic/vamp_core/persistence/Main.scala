package io.magnetic.vamp_core.persistence

import akka.actor.ActorSystem
import akka.util.Timeout
import io.magnetic.vamp_core.persistence.common.operations.message.Messages
import Messages._
import io.magnetic.vamp_core.model.{BreedDependency, Deployable, Breed, Trait}
import io.magnetic.vamp_core.persistence.slick.model.Schema
import io.magnetic.vamp_core.persistence.slick.operations.actor.Dispatch

import scala.concurrent.Await
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database
import scala.concurrent.duration._
import akka.pattern.ask


object Main {
  def main(args: Array[String]) {
    val db: Database = Database.forConfig("h2mem1")

    implicit val session = db.createSession()
    val schema = new Schema(H2Driver, session)
    schema.createDatabase

    implicit val system = ActorSystem("vamp-persistence")
    
    val dispatch = system.actorOf(Dispatch.props(schema))
    val awaitDuration = 5000 millis
    implicit val timeout = Timeout(awaitDuration)
    
    val breed = Breed(
      "wp-stackable", 
      Deployable("wordpress"), 
      List(Trait("port", "PORT", "8811", Trait.Type.Port, Trait.Direction.IN), Trait("port2", "PORT", "8822", Trait.Type.Port, Trait.Direction.OUT)),
      List(BreedDependency("mysql"))
    )
    
    println(Await.result(dispatch ? BreedOps(request = SaveBreed(breed)), awaitDuration))
    
    println(Await.result(dispatch ? BreedOps(request = SaveBreed(breed.copy(name = "wp-test"))), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = ListBreeds()), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = GetBreed("wp-test")), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = DeleteBreed("wp-test")), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = GetBreed("wp-test")), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = DeleteBreed("wp-test")), awaitDuration))

    println(Await.result(dispatch ? BreedOps(request = ListBreeds()), awaitDuration))




  }
}
