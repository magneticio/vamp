package io.vamp.gateway_driver

import akka.actor.Status.Failure
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import io.vamp.common.notification.{ Notification, NotificationErrorException }
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.model._
import io.vamp.gateway_driver.notification.UnsupportedGatewayDriverRequest
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, Matchers, WordSpecLike }

import scala.concurrent.Future

class GatewayDriverActorSpec extends TestKit(ActorSystem("GatewayDriverActor")) with ImplicitSender with WordSpecLike with Matchers with Futures with ScalaFutures with BeforeAndAfterEach {

  var error = false

  override def beforeEach() = {
    error = false
  }

  val store: GatewayStore = new GatewayStore {

    override def info: Future[Any] = if (error) Future.failed(exception) else Future.successful("store info")

    override def write(gateways: List[Gateway], raw: Option[Array[Byte]]): Unit = {}

    override def read(): Future[List[Gateway]] = Future.successful(Nil)
  }

  val marshaller: GatewayMarshaller = new GatewayMarshaller {
    override def info: String = "marshaller info"

    override def marshall(gateways: List[Gateway]): Array[Byte] = Array()
  }

  val actor = system.actorOf(Props(new GatewayDriverActor(store, marshaller) {
    override def failure(failure: Any, `class`: Class[_ <: Notification]): Exception = new RuntimeException
  }))

  val exception = new scala.IllegalArgumentException("wrong")

  "GatewayDriverActor" should {
    "reply on infoRequest by calling driver" in {
      actor ! InfoRequest
      receiveOne(remainingOrDefault) shouldBe Map("store" -> "store info", "marshaller" -> "marshaller info")
    }
    "reply on infoRequest by calling driver error" in {
      error = true
      actor ! InfoRequest
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "give unsupportedOperation" in {
      case class NotExpected(msg: String)
      actor ! NotExpected("foo")
      val notificationErrorException = expectMsgType[NotificationErrorException]
      notificationErrorException.message should be("Unsupported gateway driver request.")
      notificationErrorException.notification shouldBe UnsupportedGatewayDriverRequest(NotExpected("foo"))
    }
  }
}

