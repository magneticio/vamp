package io.magnetic.vamp_core.persistence

import io.vamp.common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.persistence.slick.components.Components.instance._
import io.magnetic.vamp_core.persistence.store.JdbcStoreProvider
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class JdbcCrudTest extends FlatSpec with JdbcStoreProvider with Matchers {
  this: ExecutionContextProvider =>

  //destroySchema
  //upgradeSchema

  val jdbcStore = store

  it should "CRUD breeds" in {
    performCrudTest(
      firstArtifact = TestData.breed1,
      updatedFirstArtifact = TestData.breed1Updated,
      secondArtifact = TestData.breed2)
  }

  // BLUEPRINTS

  it should "CRUD scale" in {
    performCrudTest(
      firstArtifact = TestData.myScale1,
      updatedFirstArtifact = TestData.myScale1Updated,
      secondArtifact = TestData.myScale2)
  }

  it should "CRUD filter" in {
    performCrudTest(
      firstArtifact = TestData.filter1,
      updatedFirstArtifact = TestData.filter1Updated,
      secondArtifact = TestData.filter2)
  }

  it should "Create an anonymous filter (but not read it)" in {
    val firstArtifact = TestData.filter1.copy(name = "")
    jdbcStore.create(firstArtifact, ignoreIfExists = false) shouldBe firstArtifact
    jdbcStore.read(firstArtifact.name, firstArtifact.getClass) shouldBe None
  }

  it should "CRUD Routing " in {
    performCrudTest(
      firstArtifact = TestData.routeSimple1,
      updatedFirstArtifact = TestData.routeSimple1Updated,
      secondArtifact = TestData.routeSimple2)
  }

  it should "CRUD Routing complete " in {
    performCrudTest(
      firstArtifact = TestData.route4,
      updatedFirstArtifact = TestData.route4Updated,
      secondArtifact = TestData.route5)
  }

  it should "CRUD escalations without parameters" in {
    performCrudTest(
      firstArtifact = TestData.escalation1,
      updatedFirstArtifact = TestData.escalation1Updated,
      secondArtifact = TestData.escalation2)
  }

  it should "CRUD escalations with parameters" in {
    performCrudTest(
      firstArtifact = TestData.escalation4,
      updatedFirstArtifact = TestData.escalation4Updated,
      secondArtifact = TestData.escalation5)
  }

  it should "CRUD sla without escalations or parameters" in {
    performCrudTest(
      firstArtifact = TestData.sla1,
      updatedFirstArtifact = TestData.sla1Updated,
      secondArtifact = TestData.sla2)
  }

  it should "CRUD sla" in {
    performCrudTest(
      firstArtifact = TestData.sla4,
      updatedFirstArtifact = TestData.sla4Updated,
      secondArtifact = TestData.sla5)
  }


  it should "CRUD blueprint-minimal with simple service" in {
    performCrudTest(
      firstArtifact = TestData.blueprintMinimal,
      updatedFirstArtifact = TestData.blueprintMinimal,
      secondArtifact = TestData.blueprintMinimalUpdatedWithCluster.copy(name = "with-cluster"))
  }

  it should "CRUD blueprint-minimal with full service" in {
    performCrudTest(
      firstArtifact = TestData.blueprintWithFullService,
      updatedFirstArtifact = TestData.blueprintWithFullServiceUpdated,
      secondArtifact = TestData.blueprintMinimalUpdatedWithCluster.copy(name = "with-cluster2"))
  }

  it should "CRUD blueprint-minimal with full sla" in {
    performCrudTest(
      firstArtifact = TestData.blueprintWithFullSla,
      updatedFirstArtifact = TestData.blueprintWithFullSlaUpdated,
      secondArtifact = TestData.blueprintMinimalUpdatedWithCluster.copy(name = "with-cluster2"))
  }

  it should "CRUD blueprint-full" in {
    var bp1 = TestData.blueprintFull
    performCrudTest(
      firstArtifact = bp1,
      updatedFirstArtifact = bp1.copy(clusters = List.empty),
      secondArtifact = bp1.copy(name = "bp2"))
  }

  it should "CRUD depployment-1" in {
    //var bp1 = TestData.blueprintFull
    performCrudTest(
      firstArtifact = TestData.deployment1,
      updatedFirstArtifact = TestData.deployment1Updated,
      secondArtifact = TestData.deployment2)
  }


  def performCrudTest(firstArtifact: Artifact, updatedFirstArtifact: Artifact, secondArtifact: Artifact): Unit = {
    // Create & read artifact
    jdbcStore.create(firstArtifact, ignoreIfExists = false) shouldBe firstArtifact
    jdbcStore.read(firstArtifact.name, firstArtifact.getClass) shouldBe Some(firstArtifact)

    // update existing artifact
    jdbcStore.update(updatedFirstArtifact, create = false) shouldBe updatedFirstArtifact
    jdbcStore.read(firstArtifact.name, firstArtifact.getClass) shouldBe Some(updatedFirstArtifact)

    // Read non-existing artifact
    jdbcStore.read(secondArtifact.name, firstArtifact.getClass) shouldBe None

    // Update non-existing artifact
    jdbcStore.update(secondArtifact, create = true) shouldBe secondArtifact

    // create existing artifact
    jdbcStore.create(firstArtifact, ignoreIfExists = true) shouldBe updatedFirstArtifact

    jdbcStore.all(firstArtifact.getClass) should contain theSameElementsAs List(updatedFirstArtifact, secondArtifact)
    jdbcStore.delete(firstArtifact.name, firstArtifact.getClass) shouldBe updatedFirstArtifact
    // TODO test for exception
    //jdbcStore.delete(firstArtifact.name, firstArtifact.getClass) shouldBe updatedFirstArtifact

    jdbcStore.delete(secondArtifact.name, secondArtifact.getClass) shouldBe secondArtifact
    // All artifacts should now be removed
    jdbcStore.all(firstArtifact.getClass) shouldBe List.empty
  }

}
