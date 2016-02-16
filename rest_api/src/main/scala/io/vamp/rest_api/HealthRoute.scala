package io.vamp.rest_api

import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, _ }
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.HealthController
import spray.http.StatusCodes.{ NotFound, OK }

trait HealthRoute extends HealthController with ExecutionContextProvider {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val healthRoutes = pathPrefix("health") {
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
