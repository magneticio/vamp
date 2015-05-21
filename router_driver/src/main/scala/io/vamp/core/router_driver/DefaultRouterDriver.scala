package io.vamp.core.router_driver

import com.typesafe.scalalogging.Logger
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.core.model.artifact._
import io.vamp.core.router_driver
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver with DefaultRouterDriverNameMatcher {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))

  def info: Future[Any] = {
    logger.debug(s"router info")
    RestClient.request[Any](s"GET $url/v1/info")
  }

  def all: Future[DeploymentRoutes] = {
    logger.debug(s"router get all")
    RestClient.request[List[Route]](s"GET $url/v1/routes").map(deploymentRoutes)
  }

  def deploymentRoutes(routes: List[Route]): DeploymentRoutes = {
    val clusterRoutes = routes.filter(route => processableClusterRoute(route.name)).map(route => ClusterRoute(clusterRouteNameMatcher(route.name), route.port, services(route, route.services)))
    val endpointRoutes = routes.filter(route => processableEndpointRoute(route.name)).map(route => EndpointRoute(endpointRouteNameMatcher(route.name), route.port, services(route, route.services)))

    DeploymentRoutes(clusterRoutes, endpointRoutes)
  }

  def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean) = {
    val name = clusterRouteName(deployment, cluster, port)
    create(name, route(name, deployment, Some(cluster), port), update)
  }

  def create(deployment: Deployment, port: Port, update: Boolean) = {
    val name = endpointRouteName(deployment, port)
    create(name, route(name, deployment, None, port), update)
  }

  private def create(name: String, route: Route, update: Boolean) = {
    if (update) {
      logger.info(s"router update: $name")
      RestClient.request[Any](s"PUT $url/v1/routes/$name", route)
    } else {
      logger.info(s"router create: $name")
      RestClient.request[Any](s"POST $url/v1/routes", route)
    }
  }

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) = remove(clusterRouteName(deployment, cluster, port))

  def remove(deployment: Deployment, port: Port) = remove(endpointRouteName(deployment, port))

  private def remove(name: String) = {
    logger.info(s"router remove: $name")
    RestClient.delete(s"$url/v1/routes/$name")
  }

  private def route(name: String, deployment: Deployment, cluster: Option[DeploymentCluster], port: Port) = cluster match {
    case None => Route(name, port.number, if (port.`type` == Port.Http) "http" else "tcp", filters(cluster), None, None, services(deployment, None, port))
    case Some(c) => Route(name, c.routes.get(port.number).get, if (port.`type` == Port.Http) "http" else "tcp", filters(cluster), None, None, services(deployment, cluster, port))
  }

  private def filters(cluster: Option[DeploymentCluster]): List[Filter] = {
    (cluster match {
      case None => None
      case Some(c) => c.services.flatMap { service =>
        service.routing.getOrElse(DefaultRouting("", None, Nil)).filters.flatMap({
          case filter: DefaultFilter => Filter(filter.name, filter.condition, s"${service.breed.name}") :: Nil
          case _ => Nil
        })
      }
    }) match {
      case result: List[_] => result.asInstanceOf[List[Filter]]
      case _ => Nil
    }
  }

  private def services(deployment: Deployment, cluster: Option[DeploymentCluster], port: Port): List[Service] = cluster match {
    case Some(c) =>
      c.services.map { service => router_driver.Service(s"${service.breed.name}", service.routing.getOrElse(DefaultRouting("", Some(100), Nil)).weight.getOrElse(100), service.servers.map(server(service, _, port))) }

    case None =>
      router_driver.Service(s"${port.name}", 100, servers(deployment, port)) :: Nil
  }

  private def services(route: Route, services: List[Service]): List[RouteService] = services.map { service =>
    RouteService(service.name, service.weight, service.servers, route.filters.filter(_.destination == service.name))
  }

  private def server(service: DeploymentService, server: DeploymentServer, port: Port) =
    Server(artifactName2Id(server), server.host, server.ports.get(port.number).get)

  private def servers(deployment: Deployment, port: Port): List[Server] = {
    TraitReference.referenceFor(port.name) match {
      case Some(TraitReference(cluster, _, name)) =>
        (for {
          h <- deployment.hosts.find(host => TraitReference.referenceFor(host.name) match {
            case Some(TraitReference(c, _, _)) if c == cluster => true
            case _ => false
          })
          p <- deployment.ports.find(_.name == port.name)
        } yield (h, p) match {
            case (host, routePort) =>
              deployment.clusters.find(_.name == cluster) match {
                case None => Nil
                case Some(c) =>
                  c.routes.values.find(_ == routePort.number) match {
                    case Some(_) => Server(string2Id(s"${deployment.name}_${port.number}"), host.value.get, routePort.number) :: Nil
                    case _ => Nil
                  }
              }
            case _ => Nil
          }) getOrElse Nil
      case _ => Nil
    }
  }

  private def processableClusterRoute(name: String): Boolean = name.split(nameDelimiter).size == 3

  private def processableEndpointRoute(name: String): Boolean = name.split(nameDelimiter).size == 2

  private def clusterRouteNameMatcher(id: String): (Deployment, DeploymentCluster, Port) => Boolean = { (deployment: Deployment, cluster: DeploymentCluster, port: Port) => id == clusterRouteName(deployment, cluster, port) }

  private def endpointRouteNameMatcher(id: String): (Deployment, Option[Port]) => Boolean = (deployment: Deployment, optionalPort: Option[Port]) => optionalPort match {
    case None => isDeploymentEndpoint(id, deployment)
    case Some(port) => id == endpointRouteName(deployment, port)
  }
}

trait DefaultRouterDriverNameMatcher {

  val nameDelimiter = "_"
  val idMatcher = """^[a-zA-Z0-9]+[a-zA-Z0-9.\-_]{3,64}$""".r

  def clusterRouteName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(cluster)}$nameDelimiter${port.number}"

  def endpointRouteName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.number}"

  def isDeploymentEndpoint(id: String, deployment: Deployment): Boolean =
    id.startsWith(s"${artifactName2Id(deployment)}$nameDelimiter")

  def artifactName2Id(artifact: Artifact) = string2Id(artifact.name)

  def string2Id(string: String) = string match {
    case idMatcher(_*) => string
    case _ => Hash.hexSha1(string)
  }
}
