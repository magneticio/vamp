package io.magnetic.vamp_core.router_driver

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

case class ClusterRoute(deploymentName: String, clusterName: String, portNumber: Int, services: List[Service])

trait RouterDriver {

  def all: Future[List[ClusterRoute]]

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port)

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port)
}

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))

  private val routes = new mutable.LinkedHashMap[String, Route]()

  def all: Future[List[ClusterRoute]] = {
    logger.debug(s"router get all")
    //new Marathon(url).apps.map(apps => apps.apps.map(app => ContainerService(deploymentName(app.id), breedName(app.id), scale = DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => DeploymentServer(task.host)))).toList)
    Future {
      routes.values.map(route => ClusterRoute(deploymentName(route.name), clusterName(route.name), route.port, route.services)).toList
    }
  }

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.debug(s"router update: $name")
    routes.put(name, route(deployment, cluster, port))
    //new Marathon(url).createApp(CreateApp(id, CreateContainer(CreateDocker(breed.deployable.name, breed.ports.map(port => CreatePortMappings(port.value.get)))), scale.instances, scale.cpu, scale.memory, Map()))
  }

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.info(s"router remove: $name")
    routes.remove(name)
    //new Marathon(url).deleteApp(id)
  }

  private def routeName(deployment: Deployment, cluster: DeploymentCluster, port: Port) = s"/${deployment.name}/${cluster.name}/${port.value.get}"

  private def deploymentName(name: String) = name.split('/').apply(1)

  private def clusterName(name: String) = name.split('/').apply(2)

  private def route(deployment: Deployment, cluster: DeploymentCluster, port: Port) =
    Route(routeName(deployment, cluster, port), port.value.get, if (port.isInstanceOf[HttpPort]) "http" else "tcp", Nil, None, None, services(deployment, cluster, port))

  private def services(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val size = cluster.services.size
    val weight = Math.round(100 / size)

    cluster.services.view.zipWithIndex.map { case (service, index) =>
      router_driver.Service(s"${service.breed.name}", if (index == size - 1) 100 - index * weight else weight, service.servers.map(server => Server(server.host, server.host, port.value.get)))
    }.toList
  }
}
