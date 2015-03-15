package io.magnetic.vamp_core.container_driver.marathon

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.crypto.Hash
import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.container_driver.marathon.api._
import io.magnetic.vamp_core.container_driver.{ContainerDriver, ContainerServer, ContainerService}
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
    RestClient.request[Apps](s"GET $url/v2/apps?embed=apps.tasks").map(apps => apps.apps.filter(app => processable(app.id)).map(app => containerService(app)).toList)
  }

  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => ContainerServer(task.id, task.host, task.ports)))

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    logger.info(s"marathon create app: $id")

    val scale = service.scale.get
    val app = CreateApp(id, CreateContainer(CreateDocker(service.breed.deployable.name, portMappings(deployment, cluster, service))), scale.instances, scale.cpu, scale.memory, environment(deployment, cluster, service))

    RestClient.request[Any](s"POST $url/v2/apps", app)
  }

  private def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[CreatePortMapping] = {
    service.breed.ports.map({ port =>
      port.direction match {
        case Trait.Direction.Out => CreatePortMapping(port.value.get)
        case Trait.Direction.In =>
          CreatePortMapping(deployment.parameters.find({
            case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), value), _) if scope == cluster.name && value == port.name.value => true
            case _ => false
          }).get._2.toString.toInt)
      }
    })
  }

  private def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] = {
    def matchParameter(ev: EnvironmentVariable, parameter: Trait.Name): Boolean = {
      ev.name match {
        case Trait.Name(None, None, value) =>
          parameter.scope.isDefined && parameter.scope.get == cluster.name && parameter.group == Some(Trait.Name.Group.EnvironmentVariables) && parameter.value == value

        case Trait.Name(Some(scope), group, value) =>
          parameter.scope == service.dependencies.get(scope) && parameter.group == group && parameter.value == value

        case _ => false
      }
    }

    service.breed.environmentVariables.filter(_.direction == Trait.Direction.In).flatMap({ ev =>
      deployment.parameters.find({ case (name, value) => matchParameter(ev, name)}) match {
        case Some((name, value)) =>
          (ev.alias match {
            case None => ev.name.value
            case Some(alias) => alias
          }) -> value.toString :: Nil
        case _ => Nil
      }
    }).toMap
  }

  def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    logger.info(s"marathon delete app: $id")
    RestClient.delete(s"$url/v2/apps/$id")
  }

  private def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  private def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name)
  }
}

