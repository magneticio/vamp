package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.{Config, Namespace}
import io.vamp.common.http.HttpApiDirectives
import io.vamp.model.artifact.{Deployment, Gateway}
import io.vamp.operation.controller.utilcontroller.EventPeekController

import scala.concurrent.Future

trait MetricsRoute extends AbstractRoute with EventPeekController {
  this: HttpApiDirectives ⇒

  private val window = Config.duration("vamp.operation.metrics.window")

  def metricsRoutes(implicit namespace: Namespace, timeout: Timeout): Route = pathPrefix("metrics") {
    get {
      path(Gateway.kind / Segment / Segment) { (gateway, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$gateway" :: s"metrics:$metrics" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Gateway.kind / Segment / "routes" / Segment / Segment) { (gateway, route, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$gateway" :: s"routes:$route" :: s"metrics:$metrics" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "ports" / Segment / Segment) { (deployment, cluster, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$deployment/$cluster/$port" :: s"metrics:$metrics" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment / "ports" / Segment / Segment) { (deployment, cluster, service, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$deployment/$cluster/$port" :: s"routes:$deployment/$cluster/$service" :: s"metrics:$metrics" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment / "ports" / Segment / Segment) { (deployment, cluster, service, instance, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(Future.successful[Option[Any]](None)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      }
    }
  }
}
