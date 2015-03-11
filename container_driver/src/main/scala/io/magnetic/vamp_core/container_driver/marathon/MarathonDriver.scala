package io.magnetic.vamp_core.container_driver.marathon

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.crypto.Hash
import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.container_driver.marathon.api._
import io.magnetic.vamp_core.container_driver.{ContainerDriver, ContainerService}
import io.magnetic.vamp_core.model.artifact._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class MarathonDriver(ec: ExecutionContext, url: String) extends ContainerDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[MarathonDriver]))

  private val nameDelimiter = "/"
  private val idMatcher = """^\w[\w-]*$""".r

  def all: Future[List[ContainerService]] = {
    logger.debug(s"marathon get all")
    RestClient.request[Apps](s"GET $url/v2/apps?embed=apps.tasks").map(apps => apps.apps.filter(app => processable(app.id)).map(app => ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => DeploymentServer(task.host)))).toList)
  }

  def deploy(deployment: Deployment, breed: DefaultBreed, scale: DefaultScale) = {
    val id = appId(deployment, breed)
    logger.debug(s"marathon create app: $id")
    RestClient.request[Any](s"POST $url/v2/apps", CreateApp(id, CreateContainer(CreateDocker(breed.deployable.name, breed.ports.map(port => CreatePortMappings(port.value.get)))), scale.instances, scale.cpu, scale.memory, Map()))
  }

  def undeploy(deployment: Deployment, breed: DefaultBreed) = {
    val id = appId(deployment, breed)
    logger.info(s"marathon delete app: $id")
    RestClient.request[Any](s"DELETE $url/v2/apps/$id")
  }

  private def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  private def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name)
  }
}

