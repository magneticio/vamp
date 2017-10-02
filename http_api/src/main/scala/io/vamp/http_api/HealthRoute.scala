package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{ NotFound, OK }
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.HealthController

import io.vamp.model.artifact.{ Gateway, Deployment }

trait HealthRoute extends AbstractRoute with HealthController {
  this: HttpApiDirectives ⇒

  def healthRoutes(implicit namespace: Namespace, timeout: Timeout): Route = pathPrefix("health") {
    get {
      path(Gateway.kind / Segment) { gateway ⇒
        pathEndOrSingleSlash {
          onSuccess(gatewayHealth(gateway)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Gateway.kind / Segment / "routes" / Segment) { (gateway, route) ⇒
        pathEndOrSingleSlash {
          onSuccess(routeHealth(gateway, route)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment) { deployment ⇒
        pathEndOrSingleSlash {
          onSuccess(deploymentHealth(deployment)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment) { (deployment, cluster) ⇒
        pathEndOrSingleSlash {
          onSuccess(clusterHealth(deployment, cluster)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment) { (deployment, cluster, service) ⇒
        pathEndOrSingleSlash {
          onSuccess(serviceHealth(deployment, cluster, service)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment) { (deployment, cluster, service, instance) ⇒
        pathEndOrSingleSlash {
          onSuccess(instanceHealth(deployment, cluster, service, instance)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      }
    }
  }
}
