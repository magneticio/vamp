package io.vamp.common.akka

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.notification.Notification
import io.vamp.common.{ ClassMapper, Namespace, NamespaceProvider }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class IoCSpec extends TestKit(ActorSystem("IoCSpec")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with NamespaceProvider
    with LazyLogging {

  implicit val namespace: Namespace = Namespace("default")
  implicit val timeout: Timeout = Timeout(5L, TimeUnit.SECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Echo actor" must {

    "echo message" in {

      val testProbe = TestProbe("test")

      val actors = Await.result(IoC.createActor(Props(classOf[EchoActor])).map(_ :: Nil)(system.dispatcher), 5.seconds)
      val actor = actors.head
      val testMessage = "Example Message"
      testProbe.send(actor, testMessage)
      testProbe.expectMsgPF(30.seconds) {
        case response: String ⇒
          logger.info(response.toString)
          assert(response == testMessage)
        case _ ⇒
          fail("Unexpected message")
      }
    }
  }
}

class EchoActorMapper extends ClassMapper {
  val name = "echo"
  val clazz: Class[_] = classOf[EchoActor]
}

class EchoActor extends CommonSupportForActors {
  override def receive: Receive = {
    case text: String ⇒ reply(echo(text))
  }

  private def echo(text: String): Future[String] = Future { text }

  override def message(notification: Notification): String = "echo actor message"

  override def info(notification: Notification): Unit = log.info(s"echo actor info")

  override def reportException(notification: Notification): Exception = new Exception("Echo actor notification report")
}
