package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{ NotFound, OK }
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.HealthController

trait HealthRoute extends AbstractRoute with HealthController {
  this: HttpApiDirectives ⇒

  def healthRoutes(implicit namespace: Namespace, timeout: Timeout) = pathPrefix("health") {
    get {
      path("gateways" / Segment) { gateway ⇒
        pathEndOrSingleSlash {
          onSuccess(gatewayHealth(gateway)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("gateways" / Segment / "routes" / Segment) { (gateway, route) ⇒
        pathEndOrSingleSlash {
          onSuccess(routeHealth(gateway, route)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment) { deployment ⇒
        pathEndOrSingleSlash {
          onSuccess(deploymentHealth(deployment)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment) { (deployment, cluster) ⇒
        pathEndOrSingleSlash {
          onSuccess(clusterHealth(deployment, cluster)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment / "services" / Segment) { (deployment, cluster, service) ⇒
        pathEndOrSingleSlash {
          onSuccess(serviceHealth(deployment, cluster, service)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment) { (deployment, cluster, service, instance) ⇒
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
