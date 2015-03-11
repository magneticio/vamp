package io.magnetic.vamp_core.router_driver

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.crypto.Hash
import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

case class ClusterRoute(matching: (Deployment, DeploymentCluster, Port) => Boolean, services: List[Service])

trait RouterDriver {

  def all: Future[List[ClusterRoute]]

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]
}

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))

  private val nameDelimiter = "_"
  private val idMatcher = """^\w[\w-]*$""".r

  def all: Future[List[ClusterRoute]] = {
    logger.debug(s"router get all")
    RestClient.request[List[Route]](s"GET $url/v1/routes").map(routes => routes.filter(route => processable(route.name)).map(route => ClusterRoute(nameMatcher(route.name), route.services)))
  }

  def update(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.info(s"router update: $name")
    RestClient.request[Any](s"POST $url/v1/routes", route(deployment, cluster, port))
  }

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val name = routeName(deployment, cluster, port)
    logger.info(s"router remove: $name")
    RestClient.delete(s"$url/v1/routes/$name")
  }

  private def route(deployment: Deployment, cluster: DeploymentCluster, port: Port) =
    Route(routeName(deployment, cluster, port), port.value.get, if (port.isInstanceOf[HttpPort]) "http" else "tcp", Nil, None, None, services(deployment, cluster, port))

  private def services(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val size = cluster.services.size
    val weight = Math.round(100 / size)

    cluster.services.view.zipWithIndex.map { case (service, index) =>
      router_driver.Service(s"${service.breed.name}", if (index == size - 1) 100 - index * weight else weight, service.servers.map(server(service, _, port)))
    }.toList
  }

  private def server(service: DeploymentService, server: DeploymentServer, port: Port) = {
    Server(server.name, server.host, server.ports.get(port.value.get).get)
  }

  private def routeName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(cluster)}$nameDelimiter${port.value.get}"

  private def processable(name: String): Boolean = name.split(nameDelimiter).size == 3

  private def nameMatcher(id: String): (Deployment, DeploymentCluster, Port) => Boolean = { (deployment: Deployment, cluster: DeploymentCluster, port: Port) => id == routeName(deployment, cluster, port) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name)
  }
}
