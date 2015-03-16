package io.magnetic.vamp_core.router_driver

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.crypto.Hash
import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

case class DeploymentRoutes(clusterRoutes: List[ClusterRoute], endpointRoutes: List[EndpointRoute])

trait DeploymentRoute {
  def port: Int

  def services: List[Service]
}

case class ClusterRoute(matching: (Deployment, DeploymentCluster, Port) => Boolean, port: Int, services: List[Service]) extends DeploymentRoute

case class EndpointRoute(matching: (Deployment, Port) => Boolean, port: Int, services: List[Service]) extends DeploymentRoute

trait RouterDriver {

  def all: Future[DeploymentRoutes]

  def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]

  def create(deployment: Deployment, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, port: Port): Future[Any]
}

class DefaultRouterDriver(ec: ExecutionContext, url: String) extends RouterDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DefaultRouterDriver]))

  private val nameDelimiter = "_"
  private val idMatcher = """^\w[\w-]*$""".r

  def all: Future[DeploymentRoutes] = {
    logger.debug(s"router get all")
    RestClient.request[List[Route]](s"GET $url/v1/routes").map(deploymentRoutes)
  }

  def deploymentRoutes(routes: List[Route]): DeploymentRoutes = {
    val clusterRoutes = routes.filter(route => processableClusterRoute(route.name)).map(route => ClusterRoute(clusterRouteNameMatcher(route.name), route.port, route.services))
    val endpointRoutes = routes.filter(route => processableEndpointRoute(route.name)).map(route => EndpointRoute(endpointRouteNameMatcher(route.name), route.port, route.services))

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
    case None => Route(name, port.value.get, if (port.isInstanceOf[HttpPort]) "http" else "tcp", filters(cluster), None, None, services(deployment, None, port))
    case Some(c) => Route(name, c.routes.get(port.value.get).get, if (port.isInstanceOf[HttpPort]) "http" else "tcp", filters(cluster), None, None, services(deployment, cluster, port))
  }

  private def filters(cluster: Option[DeploymentCluster]): List[Filter] = {
    (cluster match {
      case None => None
      case Some(c) => c.services.flatMap { service =>
        service.routing.filters.flatMap({
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
      c.services.map { service => router_driver.Service(s"${service.breed.name}", service.routing.weight.getOrElse(100), service.servers.map(server(service, _, port)))}.toList

    case None =>
      router_driver.Service(s"${port.name}", 100, servers(deployment, port)) :: Nil
  }

  private def server(service: DeploymentService, server: DeploymentServer, port: Port) =
    Server(server.name, server.host, server.ports.get(port.value.get).get)

  private def servers(deployment: Deployment, port: Port): List[Server] = {
    val list = for {
      h <- deployment.parameters.find({
        case (Trait.Name(Some(scope), None, value), _) if scope == port.name.scope.get && value == Trait.host => true
        case _ => false
      })
      p <- deployment.parameters.find({
        case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), value), _) if scope == port.name.scope.get && value == port.name.value => true
        case _ => false
      })
    } yield (h, p) match {
        case ((_, host: String), (_, routePort: Int)) =>
          deployment.clusters.find(_.name == port.name.scope.get) match {
            case None => Nil
            case Some(cluster) =>
              cluster.routes.map(_._2).find(_ == routePort) match {
                case Some(_) => Server(s"${deployment.name}_${port.value.get}", host, routePort) :: Nil
                case _ => Nil
              }
          }
        case _ => Nil
      }
    list.getOrElse(Nil)
  }

  private def clusterRouteName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(cluster)}$nameDelimiter${port.value.get}"

  private def endpointRouteName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.value.get}"

  private def processableClusterRoute(name: String): Boolean = name.split(nameDelimiter).size == 3

  private def processableEndpointRoute(name: String): Boolean = name.split(nameDelimiter).size == 2

  private def clusterRouteNameMatcher(id: String): (Deployment, DeploymentCluster, Port) => Boolean = { (deployment: Deployment, cluster: DeploymentCluster, port: Port) => id == clusterRouteName(deployment, cluster, port) }

  private def endpointRouteNameMatcher(id: String): (Deployment, Port) => Boolean = { (deployment: Deployment, port: Port) => id == endpointRouteName(deployment, port) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name)
  }
}
