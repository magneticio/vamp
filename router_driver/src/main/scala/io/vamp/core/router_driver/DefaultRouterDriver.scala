package io.vamp.core.router_driver

import com.typesafe.scalalogging.Logger
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient
import io.vamp.core.model.artifact._
import io.vamp.core.router_driver
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.matching.Regex

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver with DefaultRouterDriverNameMatcher {
  protected implicit val executionContext = ec

  private val serviceIdMatcher = """^[a-zA-Z0-9]+[a-zA-Z0-9.\-_:]{1,63}$""".r

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))

  def info: Future[Any] = {
    logger.debug(s"router info")
    RestClient.get[Any](s"$url/v1/info")
  }

  def all: Future[DeploymentRoutes] = {
    logger.debug(s"router get all")
    RestClient.get[List[Route]](s"$url/v1/routes").map(deploymentRoutes)
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
      RestClient.put[Any](s"$url/v1/routes/$name", route)
    } else {
      logger.info(s"router create: $name")
      RestClient.post[Any](s"$url/v1/routes", route)
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
          case filter: DefaultFilter => Filter(filter.name, filter.condition, s"${artifactName2Id(service.breed, serviceIdMatcher)}") :: Nil
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
      c.services.map { service => router_driver.Service(s"${artifactName2Id(service.breed, serviceIdMatcher)}", service.routing.getOrElse(DefaultRouting("", Some(100), Nil)).weight.getOrElse(100), service.servers.map(server(service, _, port))) }

    case None =>
      val name = TraitReference.referenceFor(port.name).map(_.referenceWithoutGroup).getOrElse(port.name)
      router_driver.Service(s"${string2Id(name, serviceIdMatcher)}", 100, servers(deployment, port)) :: Nil
  }

  private def services(route: Route, services: List[Service]): List[RouteService] = services.map { service =>
    RouteService(serviceRouteNameMatcher(service.name), service.weight, service.servers, route.filters.filter(_.destination == service.name))
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

  private def serviceRouteNameMatcher(id: String): (DeploymentService) => Boolean = { (deploymentService: DeploymentService) => id == artifactName2Id(deploymentService.breed, serviceIdMatcher) }

  private def endpointRouteNameMatcher(id: String): (Deployment, Option[Port]) => Boolean = (deployment: Deployment, optionalPort: Option[Port]) => optionalPort match {
    case None => isDeploymentEndpoint(id, deployment)
    case Some(port) => id == endpointRouteName(deployment, port)
  }
}

trait DefaultRouterDriverNameMatcher {

  val nameDelimiter = "_"
  val idMatcher = """^[a-zA-Z0-9]+[a-zA-Z0-9.\-_]{3,63}$""".r

  def clusterRouteName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String = {
    val id = s"${deployment.name}$nameDelimiter${cluster.name}$nameDelimiter${port.number}"
    val hashId = string2Id(id)

    if (hashId == id) id else s"${id.substring(0, 8)}$nameDelimiter$hashId"
  }

  def endpointRouteName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.number}"

  def isDeploymentEndpoint(id: String, deployment: Deployment): Boolean =
    id.startsWith(s"${artifactName2Id(deployment)}$nameDelimiter")

  def artifactName2Id(artifact: Artifact, matcher: Regex = idMatcher) = string2Id(artifact.name, matcher)

  def string2Id(string: String, matcher: Regex = idMatcher) = string match {
    case matcher(_*) => string
    case _ => Hash.hexSha1(string)
  }
}
