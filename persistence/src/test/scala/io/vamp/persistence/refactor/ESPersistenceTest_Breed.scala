package io.vamp.persistence.refactor

import io.vamp.common.{Namespace, RootAnyMap}
import io.vamp.model.artifact._
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_Breed extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"

  it should "Correctly Persist Breed objects" in { implicit namespace: Namespace ⇒
    val breed1 = DefaultBreed(name = "testDefaultBreed", metadata = RootAnyMap.empty, deployable = Deployable(Some("someType"), "someName"),
      ports = List[Port](Port(1)), environmentVariables = Nil, constants = Nil, arguments = Nil,
      dependencies = Map(), healthChecks = None)

    // Create and retrieve; See that the object is there
    val breed1Id = simpleAwait(VampPersistence().create(breed1.asInstanceOf[Breed], false))
    assert(simpleAwait(VampPersistence().read(breed1Id)) == breed1)

    val breed1Updated = breed1.copy(ports = Port(2) :: breed1.ports)
    simpleAwait(VampPersistence().createOrUpdate[Breed](breed1Updated.asInstanceOf[Breed], false))
    assert(simpleAwait(VampPersistence().read(breed1Id)) == breed1Updated)

  }
}
