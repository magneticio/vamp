package io.vamp.gateway_driver

import akka.actor.Status.Failure
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import io.vamp.common.notification.{ Notification, NotificationErrorException }
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.notification.UnsupportedGatewayDriverRequest
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService, Port }
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, Matchers, WordSpecLike }

import scala.concurrent.Future

class GatewayDriverActorSpec extends TestKit(ActorSystem("GatewayDriverActor")) with ImplicitSender with WordSpecLike with Matchers with Futures with ScalaFutures with BeforeAndAfterEach {

  var error = false

  override def beforeEach() = {
    error = false
  }

  val driver: RouterDriver = new RouterDriver {

    def all: Future[DeploymentRoutes] = if (error) Future.failed(exception) else Future.successful(deploymentRoutes)

    def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any] = if (error) Future.failed(exception) else Future.successful("removed")

    def remove(deployment: Deployment, port: Port): Future[Any] = if (error) Future.failed(exception) else Future.successful("removed2")

    def info: Future[Any] = if (error) Future.failed(exception) else Future.successful("info")

    def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean): Future[Any] = if (error) Future.failed(exception) else Future.successful("create")

    def create(deployment: Deployment, port: Port, update: Boolean): Future[Any] = if (error) Future.failed(exception) else Future.successful("create2")
  }
  val actor = system.actorOf(Props(new GatewayDriverActor(driver) {
    override def failure(failure: Any, `class`: Class[_ <: Notification]): Exception = new RuntimeException
  }))

  val services = List(GatewayService((service: DeploymentService) ⇒ {
    service.breed.name == "foo"
  }, 100, List(Server("name", "host", 123)), Nil))
  val clusterRoutes = List(ClusterGateway({ (x, y, z) ⇒ true }, 1243, services))
  val endpointRoutes: List[EndpointRoute] = List(EndpointRoute({ (a, b) ⇒ true }, 23323, services))
  val deploymentRoutes = DeploymentRoutes(clusterRoutes, endpointRoutes)
  val exception = new scala.IllegalArgumentException("wrong")
  val deployment: Deployment = Deployment("someDeploy", List(DeploymentCluster("cluster1", Nil, None, Map.empty, Map.empty)), Nil, Nil, Nil, Nil)
  val cluster: DeploymentCluster = DeploymentCluster("deployCluster", Nil, None, Map.empty, Map.empty)
  val port = Port.portFor(231)

  "GatewayDriverActor" should {
    "reply on infoRequest by calling driver" in {
      actor ! InfoRequest
      receiveOne(remainingOrDefault) shouldBe "info"
    }
    "reply on infoRequest by calling driver error" in {
      error = true
      actor ! InfoRequest
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "handle remove" in {
      actor ! GatewayDriverActor.Remove(deployment, cluster, port)
      receiveOne(remainingOrDefault) shouldBe "removed"
    }
    "handle remove error" in {
      error = true
      actor ! GatewayDriverActor.Remove(deployment, cluster, port)
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "handle removeEndpoint" in {
      actor ! GatewayDriverActor.RemoveEndpoint(deployment, port)
      receiveOne(remainingOrDefault) shouldBe "removed2"
    }
    "handle removeEndpoint error" in {
      error = true
      actor ! GatewayDriverActor.RemoveEndpoint(deployment, port)
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "handle Create" in {
      actor ! GatewayDriverActor.Create(deployment, cluster, port, update = true)
      receiveOne(remainingOrDefault) shouldBe "create"
    }
    "handle Create error" in {
      error = true
      actor ! GatewayDriverActor.Create(deployment, cluster, port, update = true)
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "handle CreateEndpoint" in {
      actor ! GatewayDriverActor.CreateEndpoint(deployment, port, update = true)
      receiveOne(remainingOrDefault) shouldBe "create2"
    }
    "handle CreateEndpoint error" in {
      error = true
      actor ! GatewayDriverActor.CreateEndpoint(deployment, port, update = true)
      receiveOne(remainingOrDefault) shouldBe Failure(exception)
    }
    "handle All" in {
      actor ! GatewayDriverActor.All
      receiveOne(remainingOrDefault) shouldBe deploymentRoutes
    }
    "handle All error" in {
      error = true
      actor ! GatewayDriverActor.All
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

