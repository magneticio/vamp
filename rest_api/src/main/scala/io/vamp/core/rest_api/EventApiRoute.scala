package io.vamp.core.rest_api

import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import io.vamp.core.operation.controller.EventApiController
import spray.http.StatusCodes._

trait EventApiRoute extends EventApiController {
  this: CommonSupportForActors with RestApiBase =>

  implicit def timeout: Timeout

  val eventRoutes = path("events" / "get") {
    pathEndOrSingleSlash {
      post {
        pageAndPerPage() { (page, perPage) =>
          entity(as[String]) { request =>
            onSuccess(query(request)(page, perPage)) { response =>
              respondWith(OK, response)
            }
          }
        }
      }
    }
  } ~ path("events") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request =>
          onSuccess(publish(request)) { result =>
            respondWith(Created, result)
          }
        }
      }
    }
  }
}

