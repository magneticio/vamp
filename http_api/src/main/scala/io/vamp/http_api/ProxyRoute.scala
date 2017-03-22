package io.vamp.http_api

import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.{ RequestContext, Route, RouteResult }
import akka.stream.Materializer
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.ProxyController

import scala.concurrent.Future

trait ProxyRoute extends ProxyController {
  this: HttpApiDirectives with CommonProvider ⇒

  implicit def timeout: Timeout

  implicit def materializer: Materializer

  val proxyRoute =
    path("host" / Segment / "port" ~ IntNumber / RemainingPath) {
      (host, port, path) ⇒ handle(hostPortProxy(host, port, path))
    } ~ path("gateways" / Segment / RemainingPath) {
      (gateway, path) ⇒ handle(gatewayProxy(gateway, path))
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
