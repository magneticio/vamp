package io.vamp.container_driver.docker

import io.vamp.container_driver.{AbstractContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService}
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider }
import io.vamp.model.artifact._

import scala.concurrent.{ ExecutionContext, Future }
import com.spotify.docker.client.{ DockerClient, DefaultDockerClient}
import com.spotify.docker.client.messages.{ ContainerConfig, ContainerInfo => spContainerInfo, Container => spContainer, ContainerCreation, Info}

case class DockerParameter(key: String, value: String)
case class Container(docker: Docker, `type`: String = "DOCKER")
case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

object RawDockerClient {
  
  lazy val client = DefaultDockerClient.builder().build()
  
  def lift[A, B](f: A => B) : Option[A] => Option[B] = _ map f
  def asyncCall[A,B](f: Option[A] => Option[B])(implicit ec: ExecutionContext) : Option[A] => Future[Option[B]] = a => Future {f(a)}
  
  def internalCreateContainer = lift[ContainerConfig, ContainerCreation](client.createContainer(_))
  def internalUndeployContainer = lift[String, Unit](client.killContainer(_))
  def internalAllContainer = lift[DockerClient.ListContainersParam, java.util.List[spContainer]](client.listContainers(_))
  def internalInfo = lift[String, spContainerInfo](client.inspectContainer(_))
  
  def translateToRaw(container: Option[Container]) : Option[ContainerConfig] = ???
  def translateFromRaw(creation: ContainerCreation) : String = 
    creation.id
}

/**
 * Docker driver
 *
 * Docker clients: https://docs.docker.com/engine/reference/api/remote_api_client_libraries/
 * Seems that Java clients are more up to date than Scala.
 *
 */
class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver(ec) /*extends ContainerDriver with ContainerDriverNotificationProvider*/ {

  import RawDockerClient._
  /** Duplicate code from Marathon **/ 
  
  private def parameters(service: DeploymentService): List[DockerParameter] = service.arguments.map { argument ⇒
    DockerParameter(argument.key, argument.value)
  }
  
  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
      case Deployable(schema, Some(definition)) => Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters(service))))
      case _ => None
  }
  
  def info: Future[ContainerInfo] = 
    ???

  def all: Future[List[ContainerService]] = ???

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    asyncCall[ContainerConfig, ContainerCreation](internalCreateContainer).apply((translateToRaw(container(deployment, cluster, service))))
  }

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    asyncCall[String, Unit](internalUndeployContainer).apply(Some(id))
  }
}
