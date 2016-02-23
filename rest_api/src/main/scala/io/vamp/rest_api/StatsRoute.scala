package io.vamp.rest_api

import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, _ }
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.StatsController
import spray.http.StatusCodes.OK

trait StatsRoute extends StatsController with ExecutionContextProvider {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val statsRoute = pathPrefix("stats" | "statistics") {
    pathEndOrSingleSlash {
      get {
        onSuccess(stats) { result ⇒
          respondWithStatus(OK) {
            complete(result)
          }
        }
      }
    }
  }
}
