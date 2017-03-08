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
import io.vamp.common.akka.CommonProvider
import io.vamp.model.artifact.Port
import org.slf4j.LoggerFactory

import scala.concurrent.Future

trait ProxyController extends GatewayWorkflowDeploymentResolver {
  this: CommonProvider ⇒

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  implicit def timeout: Timeout

  def gatewayProxy(gatewayName: String, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit materializer: Materializer): Future[RouteResult] = {
    gatewayFor(gatewayName).flatMap {
      case Some(gateway) if gateway.deployed && gateway.port.`type` == Port.Type.Http && gateway.service.nonEmpty ⇒
        if (upgradeToWebSocket.isDefined) {
          logger.debug(s"WebSocket gateway proxy request [$gatewayName]: $path")
          websocket(gateway.service.get.host, gateway.service.get.port.number, path, context, upgradeToWebSocket.get)
        }
        else {
          logger.debug(s"HTTP gateway proxy request [$gatewayName]: $path")
          http(gateway.service.get.host, gateway.service.get.port.number, path, context)
        }
      case _ ⇒ context.complete(BadGateway)
    }
  }

  def instanceProxy(workflowName: String, instanceName: String, portName: String, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit materializer: Materializer): Future[RouteResult] = {
    workflowFor(workflowName).flatMap {
      case Some(workflow) ⇒
        workflow.instances.find(instance ⇒ instance.name == instanceName && instance.ports.contains(portName)) match {
          case Some(instance) ⇒
            if (upgradeToWebSocket.isDefined) {
              logger.debug(s"WebSocket workflow instance proxy request [$workflowName/$instanceName/$portName]: $path")
              websocket(instance.host, instance.ports(portName), path, context, upgradeToWebSocket.get)
            }
            else {
              logger.debug(s"HTTP workflow instance proxy request [$workflowName/$instanceName/$portName]: $path")
              http(instance.host, instance.ports(portName), path, context)
            }
          case _ ⇒ context.complete(BadGateway)
        }
      case _ ⇒ context.complete(BadGateway)
    }
  }

  def instanceProxy(deploymentName: String, clusterName: String, serviceName: String, instanceName: String, portName: String, path: Path)(context: RequestContext, upgradeToWebSocket: Option[UpgradeToWebSocket])(implicit materializer: Materializer): Future[RouteResult] = {
    deploymentFor(deploymentName).flatMap {
      case Some(deployment) ⇒
        deployment.
          clusters.find(_.name == clusterName).
          flatMap(_.services.find(_.breed.name == serviceName)).
          flatMap(_.instances.find(instance ⇒ instance.name == instanceName && instance.ports.contains(portName))) match {
            case Some(instance) ⇒
              if (upgradeToWebSocket.isDefined) {
                logger.debug(s"WebSocket deployment instance proxy request [$deploymentName/$clusterName/$serviceName/$instanceName/$portName]: $path")
                websocket(instance.host, instance.ports(portName), path, context, upgradeToWebSocket.get)
              }
              else {
                logger.debug(s"HTTP deployment instance proxy request [$deploymentName/$clusterName/$serviceName/$instanceName/$portName]: $path")
                http(instance.host, instance.ports(portName), path, context)
              }

            case _ ⇒ context.complete(BadGateway)
          }
      case _ ⇒ context.complete(BadGateway)
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
}
