package io.vamp.persistence.refactor

import io.vamp.common.{Namespace, RootAnyMap}
import io.vamp.model.artifact._
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_DefaultBlueprint extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"

  it should "Correctly Persist DefaultBlueprint objects" in { implicit namespace: Namespace ⇒
    val blueprint1 = DefaultBlueprint(name = "blueprint001", metadata = RootAnyMap.empty,
    clusters = List(Cluster(name = "sava", metadata = RootAnyMap.empty, services = Nil, gateways = Nil, healthChecks = Some(Nil))),
    gateways = Nil, environmentVariables = Nil,
    dialects = RootAnyMap.empty)

    // Create and retrieve; See that the object is there
    val blueprint1Id = simpleAwait(VampPersistence().create(blueprint1.asInstanceOf[Blueprint], false))
    assert(simpleAwait(VampPersistence().read(blueprint1Id)) == blueprint1)

    val blueprint2: Blueprint = blueprint1.copy(name = "blueprint002")
    // Create and retrieve; See that the object is there
    val blueprint2Id = simpleAwait(VampPersistence().create[Blueprint](blueprint2, false))
    assert(simpleAwait(VampPersistence().read(blueprint2Id)) == blueprint2)
  }

}
