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
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database

@RunWith(classOf[JUnitRunner])
class BreedCrudTest extends FlatSpec with Matchers{
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
  
    val breed2 = breed.copy(name = "wp-test")
  
    it should "Save breed should return a breed back" in {
      Await.result(dispatch ? BreedOps(request = SaveBreed(breed)), awaitDuration) shouldBe breed
      Await.result(dispatch ? BreedOps(request = SaveBreed(breed2)), awaitDuration) shouldBe breed2
    }
  
    it should "Return a list of previously saved breeds, that is two" in {
      Await.result(dispatch ? BreedOps(request = ListBreeds()), awaitDuration).asInstanceOf[List[Breed]] should contain theSameElementsAs  List(breed, breed2)
    }

    it should "Return a breed by name" in {
      Await.result(dispatch ? BreedOps(request = GetBreed("wp-test")), awaitDuration) shouldBe breed2
    }

    it should "Delete a breed an return OperationSuccess message" in {
      Await.result(dispatch ? BreedOps(request = DeleteBreed("wp-test")), awaitDuration) shouldBe OperationSuccess
    }
    
    it should "Not be able to find a previously removed breed" in {
      Await.result(dispatch ? BreedOps(request = GetBreed("wp-test")), awaitDuration) shouldBe NotFound
    }

    it should "Not be able to delete a previously removed breed" in {
      Await.result(dispatch ? BreedOps(request = DeleteBreed("wp-test")), awaitDuration) shouldBe NotFound
    }

    it should "Return only one breed in the list" in {
      Await.result(dispatch ? BreedOps(request = ListBreeds()), awaitDuration).asInstanceOf[List[Breed]] should contain theSameElementsAs  List(breed)
    }




}
