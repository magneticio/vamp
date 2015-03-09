package io.magnetic.vamp_core.container_driver

import com.typesafe.scalalogging.Logger
import io.magnetic.marathon.client.Marathon
import io.magnetic.marathon.client.api.{CreateApp, CreateContainer, CreateDocker, CreatePortMappings}
import io.magnetic.vamp_core.model.artifact._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class MarathonDriver(ec: ExecutionContext, url: String) extends ContainerDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[MarathonDriver]))

  def all: Future[List[ContainerService]] = {
    logger.debug(s"marathon get all")
    new Marathon(url).apps.map(apps => apps.apps.map(app => ContainerService(deploymentName(app.id), breedName(app.id), scale = DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => DeploymentServer(task.host)))).toList)
  }

  def deploy(deployment: Deployment, breed: DefaultBreed, scale: DefaultScale) = {
    val id = appId(deployment, breed)
    logger.debug(s"marathon create app: $id")
    new Marathon(url).createApp(CreateApp(id, CreateContainer(CreateDocker(breed.deployable.name, breed.ports.map(port => CreatePortMappings(port.value.get)))), scale.instances, scale.cpu, scale.memory, Map()))
  }

  def undeploy(deployment: Deployment, breed: DefaultBreed) = {
    val id = appId(deployment, breed)
    logger.info(s"marathon delete app: $id")
    new Marathon(url).deleteApp(id)
  }

  private def appId(deployment: Deployment, breed: DefaultBreed) = s"/${deployment.name}/${breed.name}"

  private def deploymentName(id: String) = id.split('/').apply(1)

  private def breedName(id: String) = id.split('/').apply(2)
}

