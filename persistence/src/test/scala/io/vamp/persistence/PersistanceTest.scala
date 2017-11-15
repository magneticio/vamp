package io.vamp.persistence

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.{Artifact, Id, Namespace, NamespaceProvider}
import io.vamp.persistence.global.DataStore
import io.vamp.persistence.notification.UnsupportedPersistenceRequest
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

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

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "DataStore" must {

    "store an artifact" in {

      val expectedArtifact = new SerializationArtifact

      DataStore(namespace).put(expectedArtifact.id, expectedArtifact)

      val artifact = DataStore(namespace).get(expectedArtifact.id)

      println(artifact)
    }
  }
}
