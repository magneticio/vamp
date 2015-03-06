package io.magnetic.vamp_core.container_driver

import io.magnetic.vamp_core.model.artifact._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class MarathonDriver(ec: ExecutionContext, url: String) extends ContainerDriver {
  protected implicit val executionContext = ec

  private val services = new mutable.LinkedHashMap[String, ContainerService]()

  def all: Future[List[ContainerService]] = Future {
    services.values.toList
  }

  //    offLoad(new Marathon(url).apps) match {
  //      case response: Apps =>
  //        response.apps.map { app =>
  //          ContainerService(app.id, None, None)
  //        }
  //      case any =>
  //        exception(ContainerResponseError(any))
  //        List[ContainerService]()
  //    }

  def deploy(deployment: Deployment, breed: DefaultBreed, scale: DefaultScale) =
    services.put(name(deployment, breed), ContainerService(deployment.name, breed.name, (1 to scale.instances).map({ i => DeploymentServer(s"${breed.name}/$i")}).toList))

  //    val docker = Docker(breed.deployable.name, "BRIDGE", Nil)
  //    val app = App(s"/${deployment.name}/${breed.name}", None, Nil, None, Map(), scale.instances, scale.cpu, scale.memory, 0, "", Nil, Nil, Nil, Nil, requirePorts = false, 0, Container("DOCKER", Nil, docker), Nil, Nil, UpgradeStrategy(0), "1", Nil, None, None, 0, 0, 0)
  //    new Marathon(url).createApp(app)

  def undeploy(deployment: Deployment, breed: DefaultBreed) = services.remove(name(deployment, breed))

  private def name(deployment: Deployment, breed: DefaultBreed) = s"/${deployment.name}/${breed.name}"
}
