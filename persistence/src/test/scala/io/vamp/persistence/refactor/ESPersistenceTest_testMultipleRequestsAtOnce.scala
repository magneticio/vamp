package io.vamp.persistence.refactor

import io.vamp.common.{Namespace, RootAnyMap}
import io.vamp.model.artifact._
import io.vamp.model.reader.Percentage
import io.vamp.persistence.refactor.exceptions.{DuplicateObjectIdException, InvalidObjectIdException}
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

import scala.concurrent.{Await, Future, TimeoutException}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_testMultipleRequestsAtOnce extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  val exampleGateway = Gateway(
    name = "gateway_1",
    metadata = RootAnyMap.empty,
    port = Port(name = "port01", alias = Some("portAlias01"), value = Some("value01"), number = 1, `type` = Port.Type.Http),
    service = Some(GatewayService(host = "localhost01", port = Port(name = "port01_01", alias = Some("portAlias01_01"), value = Some("value01_01"), number = 11, `type` = Port.Type.Tcp))),
    sticky = Some(Gateway.Sticky.Route), virtualHosts = List("h001", "h002", "h003"),
    routes = Nil,
    deployed = false)

  behavior of "EsDao"
  it should "Correctly handle multiple requests at once" in { implicit namespace: Namespace ⇒
    val gateway1 = exampleGateway

    // Create and retrieve; See that the object is there
    val gateway1Id = simpleAwait(VampPersistence().createOrUpdate(gateway1, false))
    the[DuplicateObjectIdException[_]] thrownBy simpleAwait(VampPersistence().create(gateway1, false))

    assert(simpleAwait(VampPersistence().read(gateway1Id)) == gateway1)


    val currentTime = System.currentTimeMillis()
    simpleAwait(Future.sequence(
      (1 to 300).map(_ => VampPersistence().update[Gateway](gateway1Id, gw => gw.copy(port = gw.port.copy(number = gw.port.number + 1)), false))
    ))

    println(s"Updating 300 times took ${System.currentTimeMillis() - currentTime} ms")

    val updatedGateway = simpleAwait(VampPersistence().read(gateway1Id))

    assert(updatedGateway.port.number == 301)

    simpleAwait(VampPersistence().deleteObject[Gateway](gateway1Id, false))

    the[InvalidObjectIdException[_]] thrownBy simpleAwait(VampPersistence().read(gateway1Id))
  }


  behavior of "Futures In General"
  it should "execute the code inside transformWith when timing out" in {implicit namespace: Namespace ⇒

    import scala.concurrent.duration._

    var transformWithMethodIsExecuted = false

    the[TimeoutException] thrownBy Await.result({
      Future{
        println("Entering the future and sleeping for 5 seconds")
        Thread.sleep(5000)
        println("This line should not be executed as the futures must have already timed out.")
      }.transformWith { result => {
        println(s"Inside the transformWithMethod after it has received the result ${result}")
        transformWithMethodIsExecuted = true
        Future.fromTry(result)
      }}
    }, 2 seconds)

    println("Exited Future")
    Thread.sleep(3500)

    println("Checking transformWith has been executed ")
    assert(transformWithMethodIsExecuted)

  }
}
