package io.vamp.persistence

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common._
import io.vamp.persistence.global.DataStore
import io.vamp.persistence.notification.UnsupportedPersistenceRequest
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import org.apache.curator.test.TestingServer


object SerializationArtifact {
  val kind: String = "TestArtifact"
}

class SerializationArtifact extends Artifact {
  override def name = "TestArtifact"

  override def kind = "TestArtifact"

  override def metadata = Map("name" → "testArtifact")
}

class PersistanceTest extends TestKit(ActorSystem("PersistanceTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with NamespaceProvider
  with LazyLogging {

  implicit val namespace: Namespace = Namespace("default")
  implicit val timeout: Timeout = Timeout(5L, TimeUnit.SECONDS)

  val zkTestServer = new TestingServer(2181)
  zkTestServer.start()

  Config.load(Map(
    "vamp.persistence.key-value-store.zookeeper.connectionString" → zkTestServer.getConnectString
  ))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    zkTestServer.stop
  }

  "DataStore" must {

    "store an artifact" in {

      val expectedArtifact = new SerializationArtifact

      DataStore(namespace).put(expectedArtifact.id, expectedArtifact)

      val artifact = DataStore(namespace).get(expectedArtifact.id)

      Thread.sleep(10000)

      println(artifact)
    }
  }
}
