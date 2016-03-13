package io.vamp.container_driver.docker

import org.scalatest._

import io.vamp.container_driver.docker.DockerDriver
import io.vamp.model.artifact._ 
import io.vamp.model.reader.MegaByte
import io.vamp.model.reader.Percentage

import scala.concurrent.duration._
import scala.concurrent._

import scala.language.postfixOps

class DockerDriverSpec extends FlatSpec with Matchers {
  
  import RawDockerClient._
  implicit val ec = scala.concurrent.ExecutionContext.global
  
  "The Docker driver" should "deploy a new container" in {
     val res = fixture.driver.deploy(fixture.deployment, fixture.deploymentCluster, fixture.deploymentService, false)
     Await.result(res, 5 seconds).asInstanceOf[Any] 
  }
  
  it should "return a list of running containers" in {
     val list = fixture.driver.all
     val containers = Await.result(list, 5 seconds)
     containers should have length 1    
  }
  
  it should "kill available container" in {

    val list = fixture.driver.all
     val containers = Await.result(list, 5 seconds)
     containers should have length 1
    
     val result = fixture.driver.undeploy(fixture.deployment, fixture.deploymentService)
     Await.result(result, 10 seconds).asInstanceOf[Any]
    
     val list2 = fixture.driver.all
     val containers2 = Await.result(list2, 5 seconds)
  
     containers2 should have length 0
  }
  
  def fixture = new {
    val ec = scala.concurrent.ExecutionContext.global 
    
    val driver = new DockerDriver(ec) 
    
    val deployable = Deployable("docker",Some("magneticio/sava:1.0.0"))
    val ports = List(Port("port",None,Some("8080/http"),8080,Port.Type.Http))
    val breed = DefaultBreed("sava:1.0.0", deployable, ports, List(), List(), List(), Map())
    val defaultScale = DefaultScale("", 0.2, MegaByte(64.0), 1)
     
    val deploymentService = DeploymentService(DeploymentService.State.Intention.Deploy, breed, List(), Some(defaultScale), List(), List(), Map(),
                                              Map(Dialect.Docker -> Map("labels" -> Map("environment" -> "staging", "owner" -> "buffy the vamp slayer"), "net" -> "host")))
    
    val gatewayPath = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/sava:1.0.0/port", List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "sava:1.0.0", "port"))
    val defaultRoute = DefaultRoute("", gatewayPath, Some(Percentage(100)), List(), List(), None, List())
       
    val gateway = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port", Port("port",None,Some("0/http"),0,Port.Type.Http), None, List(defaultRoute), false)
    val deploymentCluster = DeploymentCluster("sava", List(deploymentService), List(gateway), None,Map("port" -> 0),Map())

    val gatewayPath1 = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port",List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "port"))
    val defaultRoute1 = DefaultRoute("", gatewayPath1, Some(Percentage(100)), List(), List(), None, List())
    val gateway1 = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/9050", Port("9050",None,Some("9050"),9050,Port.Type.Http), None, List(defaultRoute1), false)
     
    val deployment = Deployment("deployment1", List(deploymentCluster), List(gateway1), List(Port("sava.ports.port",None,Some("0"),0,Port.Type.Http)), List(), List(Host("sava.hosts.host",Some("192.168.99.100"))))   
     
  }
  
}