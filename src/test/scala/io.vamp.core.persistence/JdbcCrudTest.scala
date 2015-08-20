package io.vamp.core.persistence

import io.vamp.common.notification.NotificationErrorException
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, NotificationMessageNotRestored}
import io.vamp.core.persistence.slick.components.Components.instance._
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class JdbcCrudTest extends FlatSpec with Matchers with ScalaFutures {

  val jdbcStore = new JdbcPersistence {
    def debug(message: String) = {}

    implicit def executionContext = ExecutionContext.global
  }

  implicit val sess = jdbcStore.sess
  implicit val defaultPatience = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Millis))


  destroySchema
  upgradeSchema

  it should "CRUD breeds" in {
    performCrudTest(
      firstArtifact = TestData.breed1,
      updatedFirstArtifact = TestData.breed1Updated,
      secondArtifact = TestData.breed2)
  }
  it should "CRUD overlapping breeds" in {
    performCrudTest(
      firstArtifact = TestData.breed2,
      updatedFirstArtifact = TestData.breed2.copy(deployable = Deployable("updated")),
      secondArtifact = TestData.breed2.copy(name = "copy of breed2"))
  }

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

  it should "CRUD Routing" in {
    performCrudTest(
      firstArtifact = TestData.route4,
      updatedFirstArtifact = TestData.route4Updated,
      secondArtifact = TestData.route5)
  }

  it should "CRUD generic escalations" in {
    performCrudTest(
      firstArtifact = TestData.escalation4,
      updatedFirstArtifact = TestData.escalation4Updated,
      secondArtifact = TestData.escalation5)
  }

  it should "CRUD scaling escalations" in {
    performCrudTest(
      firstArtifact = TestData.escalation7,
      updatedFirstArtifact = TestData.escalation7Updated,
      secondArtifact = TestData.escalation8)
  }

  it should "CRUD to_one & to_all escalations" in {
    performCrudTest(
      firstArtifact = TestData.escalation11,
      updatedFirstArtifact = TestData.escalation11Updated,
      secondArtifact = TestData.escalation12)
  }

  it should "CRUD sla" in {
    performCrudTest(
      firstArtifact = TestData.sla4,
      updatedFirstArtifact = TestData.sla4Updated,
      secondArtifact = TestData.sla5)
  }

  it should "CRUD sla with actual types" in {
    performCrudTest(
      firstArtifact = TestData.sla7,
      updatedFirstArtifact = TestData.sla7Updated,
      secondArtifact = TestData.sla8)
  }

  it should "CRUD blueprint with full service" in {
    performCrudTest(
      firstArtifact = TestData.blueprintWithFullService,
      updatedFirstArtifact = TestData.blueprintWithFullServiceUpdated,
      secondArtifact = TestData.blueprintMinimalUpdatedWithCluster.copy(name = "with-cluster2"))
  }

  it should "CRUD blueprint with full sla" in {
    performCrudTest(
      firstArtifact = TestData.blueprintWithFullSla,
      updatedFirstArtifact = TestData.blueprintWithFullSlaUpdated,
      secondArtifact = TestData.blueprintMinimalUpdatedWithCluster.copy(name = "with-cluster3"))
  }

  it should "CRUD blueprint-full" in {
    val bp1 = TestData.blueprintFull
    performCrudTest(
      firstArtifact = bp1,
      updatedFirstArtifact = bp1.copy(clusters = List.empty),
      secondArtifact = bp1.copy(name = "bp2"))
  }

  it should "CRUD blueprint-full overlapping" in {
    val bp1 = TestData.blueprintFull.copy(name = "full-2")
    performCrudTest(
      firstArtifact = bp1,
      updatedFirstArtifact = bp1,
      secondArtifact = bp1.copy(name = "full-3"))
  }

  it should "CRUD deployment" in {
    performCrudTest(
      firstArtifact = TestData.deployment1,
      updatedFirstArtifact = TestData.deployment1Updated,
      secondArtifact = TestData.deployment2)
  }

  it should "Store a deployment state error" in {
    whenReady(jdbcStore.create(TestData.deployment5Deployed))(_ shouldBe TestData.deployment5Deployed)
    whenReady(jdbcStore.create(TestData.deployment4WithErrorService)) {
      case storedDeployment: Deployment =>
        for (cluster <- storedDeployment.clusters) {
          for (service <- cluster.services) {
            service.state match {
              case state: io.vamp.core.model.artifact.DeploymentService.Error =>
                state.notification.getClass shouldBe classOf[NotificationMessageNotRestored]
                state.notification shouldBe NotificationMessageNotRestored("Problem in cluster deployment-cluster-2, with a service containing breed wp4.")
            }
          }
        }
      case _ => fail("Deployment not created")
    }
    jdbcStore.update(TestData.deployment4WithErrorService).futureValue
    whenReady(jdbcStore.read(TestData.deployment5Deployed.name, classOf[Deployment]))(_ shouldBe Some(TestData.deployment5Deployed))
    jdbcStore.delete(TestData.deployment4WithErrorService.name, TestData.deployment4WithErrorService.getClass).futureValue
    jdbcStore.delete(TestData.deployment5Deployed.name, TestData.deployment5Deployed.getClass).futureValue
  }

  it should "Clean up left over definitions" in {
    jdbcStore.delete("my-escalation", classOf[GenericEscalation]).futureValue
    jdbcStore.delete("sla4-escalation1", classOf[GenericEscalation]).futureValue
    jdbcStore.delete("sla4-escalation2", classOf[GenericEscalation]).futureValue
    jdbcStore.delete("full-service-breed", classOf[DefaultBreed]).futureValue
    jdbcStore.delete("full-service-breed2", classOf[DefaultBreed]).futureValue
    jdbcStore.delete("filter1", classOf[DefaultFilter]).futureValue
    jdbcStore.delete("filter2", classOf[DefaultFilter]).futureValue
    jdbcStore.delete("my-filter", classOf[DefaultFilter]).futureValue
    jdbcStore.delete("my-route", classOf[DefaultRouting]).futureValue
    jdbcStore.delete("route4", classOf[DefaultRouting]).futureValue
    jdbcStore.delete("my-scale", classOf[DefaultScale]).futureValue
    jdbcStore.delete("my-scale2", classOf[DefaultScale]).futureValue
    jdbcStore.delete("sla7-escalation", classOf[GenericEscalation]).futureValue
  }

  it should "prove all tables are empty" in {
    jdbcStore.all(classOf[DefaultBlueprint], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[DefaultBreed], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[GenericEscalation], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[DefaultFilter], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[DefaultRouting], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[DefaultScale], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[GenericSla], 1, 1).futureValue.response shouldBe List.empty
    jdbcStore.all(classOf[Deployment], 1, 1).futureValue.response shouldBe List.empty

    totalNumberOfRowsInDB shouldBe 1 // There is always a row in the vamp meta data table
  }


  def performCrudTest(firstArtifact: Artifact, updatedFirstArtifact: Artifact, secondArtifact: Artifact): Unit = {
    // Create & read artifact
    whenReady(jdbcStore.create(firstArtifact, ignoreIfExists = false))(_ shouldBe firstArtifact)
    whenReady(jdbcStore.read(firstArtifact.name, firstArtifact.getClass))(_ shouldBe Some(firstArtifact))

    // update existing artifact
    whenReady(jdbcStore.update(updatedFirstArtifact, create = false))(_ shouldBe updatedFirstArtifact)
    whenReady(jdbcStore.read(firstArtifact.name, firstArtifact.getClass))(_ shouldBe Some(updatedFirstArtifact))

    // Read non-existing artifact
    whenReady(jdbcStore.read(secondArtifact.name, secondArtifact.getClass))(_ shouldBe None)

    // Update non-existing artifact
    whenReady(jdbcStore.update(secondArtifact, create = true))(_ shouldBe secondArtifact)

    // create existing artifact
    whenReady(jdbcStore.create(firstArtifact, ignoreIfExists = true))(_ shouldBe updatedFirstArtifact)

    whenReady(jdbcStore.all(firstArtifact.getClass, 1, 2))(_.response should contain theSameElementsAs List(updatedFirstArtifact, secondArtifact))
    whenReady(jdbcStore.delete(firstArtifact.name, firstArtifact.getClass))(_ shouldBe updatedFirstArtifact)

    // second delete of the artifact throws an exception
    whenReady(jdbcStore.delete(firstArtifact.name, firstArtifact.getClass).failed) { ex =>
      ex shouldBe an[NotificationErrorException]
      ex.asInstanceOf[NotificationErrorException].notification should equal(ArtifactNotFound(firstArtifact.name, firstArtifact.getClass))
    }

    whenReady(jdbcStore.delete(secondArtifact.name, secondArtifact.getClass))(_ shouldBe secondArtifact)
    // All artifacts should now be removed
    whenReady(jdbcStore.all(firstArtifact.getClass, 1, 1))(_.response shouldBe List.empty)
  }

}
