package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.BadGateway
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.{ RequestContext, Route, RouteResult }
import akka.stream.Materializer
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.ProxyController

import scala.concurrent.Future
import scala.util.Try

trait ProxyRoute extends AbstractRoute with ProxyController {
  this: HttpApiDirectives ⇒

  implicit def materializer: Materializer

  def proxyRoute(implicit namespace: Namespace, timeout: Timeout) =
    path("host" / Segment / "port" / Segment / RemainingPath) {
      (host, port, path) ⇒ Try(handle(hostPortProxy(host, port.toInt, path))).getOrElse(complete(BadGateway))
    } ~ path("gateways" / Segment / Segment / Segment / RemainingPath) {
      (name1, name2, name3, path) ⇒ handle(gatewayProxy(s"$name1/$name2/$name3", path, skip = true))
    } ~ path("gateways" / Segment / Segment / RemainingPath) {
      (name1, name2, path) ⇒ handle(gatewayProxy(s"$name1/$name2", path, skip = true))
    } ~ path("gateways" / Segment / RemainingPath) {
      (gateway, path) ⇒ handle(gatewayProxy(gateway, path, skip = false))
    } ~ path("workflows" / Segment / "instances" / Segment / "ports" / Segment / RemainingPath) {
      (workflow, instance, port, path) ⇒ handle(instanceProxy(workflow, instance, port, path))
    } ~ path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment / "ports" / Segment / RemainingPath) {
      (deployment, cluster, service, instance, port, path) ⇒ handle(instanceProxy(deployment, cluster, service, instance, port, path))
    }

  private def handle(handler: (RequestContext, Option[UpgradeToWebSocket]) ⇒ Future[RouteResult]): Route = {
    extractUpgradeToWebSocket { upgrade ⇒ context ⇒ handler(context, Option(upgrade))
    } ~ {
      context ⇒ handler(context, None)
    }
  }
}
