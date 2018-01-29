package io.vamp.container_driver.marathon

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.{Namespace, NamespaceProvider}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class MarathonDriverActorTest extends TestKit(ActorSystem("MarathonDriverActor")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with NamespaceProvider
    with LazyLogging {

  implicit val namespace: Namespace = Namespace("default")
  implicit val timeout: Timeout = Timeout(5L, TimeUnit.SECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  /* Disable this test to check backwards compatibility

  private def containers(app: App): Containers = {
    val scale = DefaultScale(Quantity(app.cpus), MegaByte(app.mem), app.instances)
    val instances = app.tasks.map(task ⇒ {
      val portsAndIpForUserNetwork = for {
        container ← app.container
        docker ← container.docker
        networkName ← Option(docker.network.getOrElse("")) // This is a hack to support 1.4 and 1.5 at the same time
        ipAddressToUse ← task.ipAddresses.headOption
        if (networkName == "USER" || app.networks.map(_.mode).contains("container"))
      } yield (ipAddressToUse.ipAddress, docker.portMappings.map(_.containerPort).flatten ++ container.portMappings.map(_.containerPort).flatten)
      portsAndIpForUserNetwork match {
        case None ⇒ {
          val network = Try(app.container.get.docker.get.network.get).getOrElse("Empty")
          logger.info(s"Ports for ${task.id} => ${task.ports} network: ${network}")
          ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)
        }
        case Some(portsAndIp) ⇒ {
          val network = Try(app.container.get.docker.get.network.get).getOrElse("Empty")
          logger.info(s"Ports (USER network) for ${task.id} => ${portsAndIp._2} network: ${network}")
          ContainerInstance(task.id, portsAndIp._1, portsAndIp._2, task.startedAt.isDefined)
        }
      }
    })
    Containers(scale, instances)
  }

  "MarathonDriverActor" must {

    "get containers method test" in {

      val appResponse = AppsResponse(List(App("/test/sava2port",1,0.2,
        Some(AppContainer(
          Some(DockerAppContainer("magneticio/sava:1.0.0",
            None,
            List()
          )),
          List(
            DockerAppContainerPort(Some(8080),None,Some(10141)),
            DockerAppContainerPort(Some(2224),None,Some(10200))
          )
        )),
        64.0,
        List(
          Task("test_sava2port.094f4222-db82-11e7-9f09-f6a8bbe7f39c",
            List(TaskIpAddress("10.142.99.231","IPv4")),
            "10.0.0.145",
            List(),
            Some("2017-12-07T19:08:42.878Z"))),
        List(),
        Some(MarathonTaskStats(MarathonSummary(MarathonStats(MarathonCounts(0,1,0,0))))),
        List(AppNetwork("container",Some("test"))))))


      // Due to actor creation problems, the method is copied from MarathonDriverActor directly
      val extractedContainers = containers(appResponse.apps.head)

      assert(extractedContainers.instances.head.ports == List(8080, 2224))

    }
  }
  */
}
