package io.magnetic.vamp_core.router_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core._
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver.RouterDriverActor.{All, Remove, RouterDriveMessage, Update}
import io.magnetic.vamp_core.router_driver.notification.{RouterDriverNotificationProvider, UnsupportedRouterDriverRequest}

import scala.collection.mutable
import scala.concurrent.duration._

object RouterDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.router.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[RouterDriverActor], args: _*)

  trait RouterDriveMessage

  object All extends RouterDriveMessage

  case class Update(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriveMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriveMessage

}

class RouterDriverActor(url: String) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupport with ActorExecutionContextProvider with RouterDriverNotificationProvider {

  implicit val timeout = RouterDriverActor.timeout

  override protected def requestType: Class[_] = classOf[RouterDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedRouterDriverRequest(request)

  private val routes = new mutable.LinkedHashMap[String, Route]()

  def reply(request: Any) = try {
    request match {
      case All => routes.values.map(route => ClusterRoute(deploymentName(route.name), clusterName(route.name), route.port, route.services))
      case Update(deployment, cluster, port) =>
        log.info(s"route update: ${deployment.name}/${cluster.name}")
        routes.put(name(deployment, cluster, port), route(deployment, cluster, port))
      case Remove(deployment, cluster, port) =>
        log.info(s"route removal: ${deployment.name}/${cluster.name}")
        routes.remove(name(deployment, cluster, port))

      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => e
  }

  private def name(deployment: Deployment, cluster: DeploymentCluster, port: Port) = s"/${deployment.name}/${cluster.name}/${port.value.get}"

  private def deploymentName(name: String) = name.split('/').apply(1)

  private def clusterName(name: String) = name.split('/').apply(2)

  private def route(deployment: Deployment, cluster: DeploymentCluster, port: Port) =
    Route(name(deployment, cluster, port), port.value.get, if (port.isInstanceOf[HttpPort]) "http" else "tcp", Nil, None, None, services(deployment, cluster, port))

  private def services(deployment: Deployment, cluster: DeploymentCluster, port: Port) = {
    val size = cluster.services.size
    val weight = Math.round(100 / size)

    cluster.services.view.zipWithIndex.map { case (service, index) =>
      router_driver.Service(s"${service.breed.name}", if (index == size - 1) 100 - index * weight else weight, service.servers.map(server => Server(server.host, server.host, port.value.get)))
    }.toList
  }
}
