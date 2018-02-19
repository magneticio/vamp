package io.vamp.operation.controller

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.{ Path, Query }
import akka.http.scaladsl.model.ws.{ UpgradeToWebSocket, WebSocketRequest }
import akka.http.scaladsl.server.{ RequestContext, RouteResult }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import io.vamp.common.{ CacheStore, Config, Namespace }
import io.vamp.model.artifact.Port
import org.slf4j.LoggerFactory

import scala.concurrent.Future

private case class HostPortCacheEntry(host: String, port: Int)

trait ProxyController extends AbstractController with GatewayWorkflowDeploymentResolver {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val cache = new CacheStore()

  private lazy val ttl = Config.duration("vamp.operation.proxy.cache.ttl")

  def gatewayProxy(gatewayName: String, path: Path, skip: Boolean)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit namespace: Namespace, timeout: Timeout, materializer: Materializer): Future[RouteResult] = {
    val key = s"gateways/$gatewayName"
    readFromCache(key) match {
      case Some((host, port)) ⇒ hostPortProxy(host, port, path, s"[$gatewayName]")(context, upgradeToWebSocket)
      case None ⇒
        gatewayFor(gatewayName).flatMap {
          case Some(gateway) if gateway.deployed && gateway.port.`type` == Port.Type.Http && gateway.service.nonEmpty ⇒
            val (host, port) = gateway.service.get.host → gateway.port.number
            writeToCache(key, host, port)
            hostPortProxy(host, port, path, s"[$gatewayName]")(context, upgradeToWebSocket)
          case _ ⇒ if (skip) context.reject() else context.complete(BadGateway)
        }
    }
  }

  def instanceProxy(workflowName: String, instanceName: String, portName: String, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit namespace: Namespace, timeout: Timeout, materializer: Materializer): Future[RouteResult] = {
    val key = s"workflows/$workflowName/$instanceName/$portName"
    readFromCache(key) match {
      case Some((host, port)) ⇒ hostPortProxy(host, port, path, s"[$workflowName/$instanceName/$portName]")(context, upgradeToWebSocket)
      case None ⇒
        workflowFor(workflowName).flatMap {
          case Some(workflow) ⇒
            workflow.instances.find(instance ⇒ instance.name == instanceName && instance.ports.contains(portName)) match {
              case Some(instance) ⇒
                val (host, port) = instance.host → instance.ports(portName)
                writeToCache(key, host, port)
                hostPortProxy(host, port, path, s"[$workflowName/$instanceName/$portName]")(context, upgradeToWebSocket)
              case _ ⇒ context.complete(BadGateway)
            }
          case _ ⇒ context.complete(BadGateway)
        }
    }
  }

  def instanceProxy(deploymentName: String, clusterName: String, serviceName: String, instanceName: String, portName: String, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit namespace: Namespace, timeout: Timeout, materializer: Materializer): Future[RouteResult] = {
    val key = s"deployments/$deploymentName/$clusterName/$serviceName/$instanceName/$portName"
    readFromCache(key) match {
      case Some((host, port)) ⇒ hostPortProxy(host, port, path, s"[$deploymentName/$clusterName/$serviceName/$instanceName/$portName]")(context, upgradeToWebSocket)
      case None ⇒
        deploymentFor(deploymentName).flatMap {
          case Some(deployment) ⇒
            deployment.
              clusters.find(_.name == clusterName).
              flatMap(_.services.find(_.breed.name == serviceName)).
              flatMap(_.instances.find(instance ⇒ instance.name == instanceName && instance.ports.contains(portName))) match {
                case Some(instance) ⇒
                  val (host, port) = instance.host → instance.ports(portName)
                  writeToCache(key, host, port)
                  hostPortProxy(host, port, path, s"[$deploymentName/$clusterName/$serviceName/$instanceName/$portName]")(context, upgradeToWebSocket)
                case _ ⇒ context.complete(BadGateway)
              }
          case _ ⇒ context.complete(BadGateway)
        }
    }
  }

  def hostPortProxy(host: String, port: Int, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit namespace: Namespace, materializer: Materializer): Future[RouteResult] = {
    hostPortProxy(host, port, path, s"[$host:$port]")(context, upgradeToWebSocket)
  }

  private def hostPortProxy(host: String, port: Int, path: Path, label: String)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit namespace: Namespace, materializer: Materializer): Future[RouteResult] = {
    if (upgradeToWebSocket.isDefined) {
      logger.debug(s"Websocket proxy request $label: $path")
      websocket(host, port, path, context, upgradeToWebSocket.get)
    }
    else {
      logger.debug(s"HTTP proxy request $label: $path")
      http(host, port, path, context)
    }
  }

  private def http(host: String, port: Int, path: Path, context: RequestContext)(implicit materializer: Materializer): Future[RouteResult] = {
    Source.single(context.request)
      .map(request ⇒ request.withHeaders(
        request.headers.filter(_.renderInRequests())
      ).withUri(
          request.uri.
          withPath(if (path.startsWith(Path.SingleSlash)) path else Path.SingleSlash ++ path).
          withQuery(
            Query(request.uri.rawQueryString.map("?" + _).getOrElse(""))
          ).toRelative
        ))
      .via(Http().outgoingConnection(host, port))
      .runWith(Sink.head)
      .flatMap(context.complete(_))
  }

  private def websocket(host: String, port: Int, path: Path, context: RequestContext, upgrade: UpgradeToWebSocket)(implicit materializer: Materializer): Future[RouteResult] = {
    val request = WebSocketRequest(
      uri = context.request.uri.withScheme("ws").
        withAuthority(host, port).
        withPath(if (path.startsWith(Path.SingleSlash)) path else Path.SingleSlash ++ path).
        withQuery(Query(context.request.uri.rawQueryString.map("?" + _).getOrElse(""))),
      extraHeaders = context.request.headers.filter(_.renderInRequests()),
      subprotocol = None
    )
    context.complete(upgrade.handleMessages(Http().webSocketClientFlow(request)))
  }

  private def readFromCache(key: String): Option[(String, Int)] = {
    logger.debug(s"Reading from proxy cache: $key")
    cache.get[HostPortCacheEntry](key).map(hp ⇒ hp.host → hp.port)
  }

  private def writeToCache(key: String, host: String, port: Int)(implicit namespace: Namespace): Unit = {
    logger.debug(s"Writing to proxy cache: $key [$host:$port]")
    cache.put[HostPortCacheEntry](key, HostPortCacheEntry(host, port), ttl())
  }
}
