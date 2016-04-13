package io.vamp.container_driver.docker

import org.scalatest._
import io.vamp.model.artifact._
import io.vamp.container_driver.rancher.RancherDriver
import io.vamp.model.reader.MegaByte
import io.vamp.model.reader.Percentage
import scala.language.reflectiveCalls
import scala.concurrent.duration._
import scala.concurrent._
import scala.language.postfixOps
import org.joda.time.DateTime
import akka.actor.ActorSystem

class RancherDriverSpec extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  /*
  "The Rancher driver" should "deploy a new stack" in {
    val deployment1 = fixture.deployment.copy(name = "deployment_" + DateTime.now())
    val res = fixture.driver.deploy(deployment1, fixture.deploymentCluster, fixture.deploymentService, false)
    
    Thread.sleep(10000)
    Await.result(res, 60 seconds).asInstanceOf[Any]
    
  }
  
  it should "get all containers" in {
    val res = fixture.driver.all
    Thread.sleep(5000)
    val res2= Await.result(res, 60 seconds).asInstanceOf[Any]
    println("RSULTADO: " + res2)
  }
  */
  
  /*
  def fixture = new {
    //val ec = scala.concurrent.ExecutionContext.global 
    implicit val _system = ActorSystem("settlements")
    val driver = new RancherDriver(_system) 
    
    val deployable = Deployable("docker",Some("magneticio/sava-1.0_monolith:0.7.0"))
    val ports = List(Port("port",None,Some("8080/http"),8080,Port.Type.Http))
    val breed = DefaultBreed("sava", deployable, ports, List(), List(), List(), Map())
    val defaultScale = DefaultScale("", 0.2, MegaByte(64.0), 2)
     
    val deploymentService = DeploymentService(DeploymentService.State.Intention.Deploy, breed, List(), Some(defaultScale), List(), List(), Map(),
                                              Map(Dialect.Docker -> Map("labels" -> Map("environment" -> "staging", "owner" -> "buffy the vamp slayer"), "net" -> "host")))
    
    val gatewayPath = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/sava:1.0.0/port", List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "sava:1.0.0", "port"))
    val defaultRoute = DefaultRoute("", gatewayPath, Some(Percentage(100)), List(), List(), None, List())
       
    val gateway = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port", Port("port",None,Some("0/http"),0,Port.Type.Http), None, List(defaultRoute), false)
    val deploymentCluster = DeploymentCluster("sava", List(deploymentService), List(gateway), None,Map("port" -> 0),Map())

    val gatewayPath1 = GatewayPath("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/sava/port",List("e1d509c0-2e50-43e4-80dd-cd0d07a853a9", "sava", "port"))
    val defaultRoute1 = DefaultRoute("", gatewayPath1, Some(Percentage(100)), List(), List(), None, List())
    val gateway1 = Gateway("e1d509c0-2e50-43e4-80dd-cd0d07a853a9/9050", Port("9050",None,Some("9050"),9050,Port.Type.Http), None, List(defaultRoute1), false)
     
    val deployment = Deployment("deployment", List(deploymentCluster), List(gateway1), List(Port("sava.ports.port",None,Some("0"),0,Port.Type.Http)), List(), List(Host("sava.hosts.host",Some("192.168.99.100"))))   
     
  }
  */

}