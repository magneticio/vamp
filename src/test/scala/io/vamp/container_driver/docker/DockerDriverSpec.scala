package io.vamp.container_driver.docker

import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Percentage, Quantity }
import org.joda.time.DateTime
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.{ postfixOps, reflectiveCalls }

@Ignore
class DockerDriverSpec extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  implicit val ec = scala.concurrent.ExecutionContext.global

  "The Docker driver" should "deploy a new container" in {
    val deployment1 = fixture.deployment.copy(name = "deployment_" + DateTime.now())
    val res = fixture.driver.deploy(deployment1, fixture.deploymentCluster, fixture.deploymentService, update = false)
    Await.result[Any](res, 60 seconds)
  }

  it should "return a list of running containers" in {
    val deployment2 = fixture.deployment.copy(name = "deployment_" + DateTime.now())
    val res = fixture.driver.deploy(deployment2, fixture.deploymentCluster, fixture.deploymentService, update = false)
    Await.result[Any](res, 60 seconds)

    val list = fixture.driver.all
    val containersList = Await.result(list, 5 seconds)

    val containersList2 = containersList.filter { x ⇒ x.matching.apply(deployment2, fixture.breed) }

    containersList2 should not be empty
    containersList2 should have length 1
  }

  it should "kill available container" in {
    val deployment3 = fixture.deployment.copy(name = "deployment_" + DateTime.now())
    val res = fixture.driver.deploy(deployment3, fixture.deploymentCluster, fixture.deploymentService, update = false)
    Await.result[Any](res, 60 seconds)

    val list = fixture.driver.all
    val containers = Await.result(list, 5 seconds)
    containers should not be empty

    val result = fixture.driver.undeploy(deployment3, fixture.deploymentService)
    Await.result[Any](result, 10 seconds)

    val list2 = fixture.driver.all
    val containers2 = Await.result(list2, 5 seconds)

    (containers.length - containers2.length) should equal(1)
  }

  def fixture = new {
    val ec = scala.concurrent.ExecutionContext.global

    val driver = new DockerDriver() {
      override implicit def executionContext: ExecutionContext = ec
    }

    val deployable = Deployable("docker", Some("magneticio/sava:1.0.0"))
    val ports = List(Port("port", None, Some("8080/http"), 8080, Port.Type.Http))
    val breed = DefaultBreed("sava:1.0.0", deployable, ports, List(), List(), List(), Map())
    val defaultScale = DefaultScale("", Quantity(0.2), MegaByte(64.0), 1)

    val deploymentService = DeploymentService(DeploymentService.State.Intention.Deploy, breed, List(), Some(defaultScale), List(), List(), Map(),
      Map(Dialect.Docker -> Map("labels" -> Map("environment" -> "staging", "owner" -> "buffy the vamp slayer"), "net" -> "host")))

    val gatewayPath = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/sava:1.0.0/port", List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "sava:1.0.0", "port"))
    val defaultRoute = DefaultRoute("", gatewayPath, Some(Percentage(100)), None, List(), List(), None, List())

    val gateway = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port", Port("port", None, Some("0/http"), 0, Port.Type.Http), None, None, Nil, List(defaultRoute), deployed = false)
    val deploymentCluster = DeploymentCluster("sava", List(deploymentService), List(gateway), None, Map())

    val gatewayPath1 = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port", List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "port"))
    val defaultRoute1 = DefaultRoute("", gatewayPath1, Some(Percentage(100)), None, List(), List(), None, List())
    val gateway1 = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/9050", Port("9050", None, Some("9050"), 9050, Port.Type.Http), None, None, Nil, List(defaultRoute1), deployed = false)

    val deployment = Deployment("deployment", List(deploymentCluster), List(gateway1), List(Port("sava.ports.port", None, Some("0"), 0, Port.Type.Http)), List(), List(Host("sava.hosts.host", Some("192.168.99.100"))))
  }
}
