package io.vamp.persistence

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.akka.IoC
import io.vamp.common.vitals.InfoRequest
import io.vamp.common.{ Artifact, Namespace, NamespaceProvider }
import io.vamp.persistence.notification.UnsupportedPersistenceRequest
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.Await
import scala.concurrent.duration._

object TestArtifact {
  val kind: String = "TestArtifact"
}

class TestArtifact extends Artifact {
  override def name = "TestArtifact"

  override def kind = "TestArtifact"

  override def metadata = Map("name" → "testArtifact")
}

class TestInMemoryPersistenceActor extends InMemoryPersistenceActor {
  override protected def type2string(`type`: Class[_]): String = `type` match {
    // test artifact
    case t if classOf[TestArtifact].isAssignableFrom(t) ⇒ TestArtifact.kind
    case _ ⇒ throwException(UnsupportedPersistenceRequest(`type`))
  }
}

class InMemoryPersistenceActorSpec extends TestKit(ActorSystem("InMemoryPersistenceActorSpec")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with NamespaceProvider
    with LazyLogging {

  implicit val namespace: Namespace = Namespace("default")
  implicit val timeout: Timeout = Timeout(5L, TimeUnit.SECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "InMemoryPersistenceActor" must {

    "reply to InfoRequest" in {

      val testProbe = TestProbe("test")

      val actors = Await.result(IoC.createActor(Props(classOf[InMemoryPersistenceActor])).map(_ :: Nil)(system.dispatcher), 5.seconds)
      val actor = actors.head
      val expectedResponse = Map("database" →
        Map(
          "status" → "valid",
          "records" → 0,
          "artifacts" → Map(),
          "type" → "in-memory [no persistence]"), "archiving" → true)
      testProbe.send(actor, InfoRequest)
      testProbe.expectMsgPF(30.seconds) {
        case response: Map[_, _] ⇒
          logger.info(response.toString)
          assert(response == expectedResponse)
        case _ ⇒
          fail("Unexpected message")
      }
    }

    "reply to Create" in {

      val testProbe = TestProbe("test")
      val actors = Await.result(IoC.createActor(Props(classOf[TestInMemoryPersistenceActor])).map(_ :: Nil)(system.dispatcher), 5.seconds)
      val actor = actors.head
      val artifact = new TestArtifact()
      val expectedResponse = List[TestArtifact](artifact)
      val source = "testSource"
      testProbe.send(actor, PersistenceActor.Create(artifact, Option(source)))
      testProbe.expectMsgPF(30.seconds) {
        case response: List[_] ⇒
          logger.info(response.toString)
          assert(response === expectedResponse)
        case _ ⇒
          fail("Unexpected message")
      }
    }
  }

}
