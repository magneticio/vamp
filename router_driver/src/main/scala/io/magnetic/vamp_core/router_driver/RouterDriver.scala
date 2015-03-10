package io.magnetic.vamp_core.router_driver

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

case class ClusterRoute(deploymentName: String, clusterName: String, portNumber: Int, services: List[Service])

trait RouterDriver {

  def all: Future[List[ClusterRoute]]

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]
}

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))
  private val nameDelimiter = '_'

  def all: Future[List[ClusterRoute]] = {
    logger.debug(s"router get all")
    RestClient.request[List[Route]](s"GET $url/v1/routes").map(routes => routes.filter(route => validName(route.name)).map(route => ClusterRoute(deploymentName(route.name), clusterName(route.name), route.port, route.services)))
  }

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.info(s"router update: $name")
    RestClient.request[Any](s"POST $url/v1/routes", route(deployment, cluster, port))
  }

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.info(s"router remove: $name")
    RestClient.request[Any](s"DELETE $url/v1/routes/$name")
  }

  private def routeName(deployment: Deployment, cluster: DeploymentCluster, port: Port) = s"$nameDelimiter${deployment.name}$nameDelimiter${cluster.name}$nameDelimiter${port.value.get}"

  private def validName(name: String) = name.split(nameDelimiter).size == 4

  private def deploymentName(name: String) = name.split(nameDelimiter).apply(1)

  private def clusterName(name: String) = name.split(nameDelimiter).apply(2)

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
