package io.magnetic.vamp_core.persistence

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core.model.{Breed, Dependency, Deployable, Trait}
import io.magnetic.vamp_core.persistence.common.operations.message.Messages
import io.magnetic.vamp_core.persistence.common.operations.message.Messages._
import io.magnetic.vamp_core.persistence.slick.model.Schema
import io.magnetic.vamp_core.persistence.slick.operations.actor.Dispatch
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database

@RunWith(classOf[JUnitRunner])
class Main extends FlatSpec{
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
      List(Trait("port", Option("PORT"), Option("8811"), Trait.Type.Port, Trait.Direction.In), Trait("port2", Option("PORT"), Option("8822"), Trait.Type.Port, Trait.Direction.Out)),
      Map("db" -> Dependency("mysql"))
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
