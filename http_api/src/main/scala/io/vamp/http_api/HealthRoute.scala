package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.{Config, Namespace}
import io.vamp.common.http.HttpApiDirectives
import io.vamp.model.artifact.{Deployment, Gateway}
import io.vamp.operation.controller.EventPeekController

import scala.concurrent.Future

trait HealthRoute extends AbstractRoute with EventPeekController {
  this: HttpApiDirectives ⇒

  private val window = Config.duration("vamp.operation.health.window")

  def healthRoutes(implicit namespace: Namespace, timeout: Timeout): Route = pathPrefix("health") {
    get {
      path(Gateway.kind / Segment) { gateway ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$gateway" :: "health" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Gateway.kind / Segment / "routes" / Segment) { (gateway, route) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"gateways:$gateway" :: s"routes:$route" :: "health" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment) { deployment ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"deployments:$deployment" :: "health" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment) { (deployment, cluster) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"deployments:$deployment" :: s"clusters:$cluster" :: "health" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment) { (deployment, cluster, service) ⇒
        pathEndOrSingleSlash {
          onSuccess(peekLastDoubleValue(s"deployments:$deployment" :: s"clusters:$cluster" :: s"services:$service" :: "health" :: Nil, window())) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment) { (deployment, cluster, service, instance) ⇒
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
