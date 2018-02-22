package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{ NotFound, OK }
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.HealthController

import io.vamp.model.artifact.{ Gateway, Deployment }

object HealthRoute {
  val path = "health"
}

trait HealthRoute extends AbstractRoute with HealthController {
  this: HttpApiDirectives ⇒

  def healthRoutes(implicit namespace: Namespace, timeout: Timeout): Route = pathPrefix(HealthRoute.path) {
    get {
      parameters("window".?) { window ⇒
        path(Gateway.kind / Segment) { gateway ⇒
          pathEndOrSingleSlash {
            onSuccess(gatewayHealth(gateway)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        } ~ path(Gateway.kind / Segment / "routes" / Segment) { (gateway, route) ⇒
          pathEndOrSingleSlash {
            onSuccess(routeHealth(gateway, route)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        } ~ path(Deployment.kind / Segment) { deployment ⇒
          pathEndOrSingleSlash {
            onSuccess(deploymentHealth(deployment)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        } ~ path(Deployment.kind / Segment / "clusters" / Segment) { (deployment, cluster) ⇒
          pathEndOrSingleSlash {
            onSuccess(clusterHealth(deployment, cluster)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment) { (deployment, cluster, service) ⇒
          pathEndOrSingleSlash {
            onSuccess(serviceHealth(deployment, cluster, service)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        } ~ path(Deployment.kind / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment) { (deployment, cluster, service, instance) ⇒
          pathEndOrSingleSlash {
            onSuccess(instanceHealth(deployment, cluster, service, instance)(window)) {
              case Some(result) ⇒ respondWith(OK, result)
              case _            ⇒ respondWith(NotFound, None)
            }
          }
        }
      }
    }
  }
}
