package io.vamp.rest_api

import akka.http.scaladsl.model.StatusCodes.OK
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.StatsController

trait StatsRoute extends StatsController {
  this: ExecutionContextProvider with ActorSystemProvider with RestApiBase ⇒

  implicit def timeout: Timeout

  val statsRoute = pathPrefix("stats" | "statistics") {
    pathEndOrSingleSlash {
      get {
        onSuccess(stats) { result ⇒
          respondWith(OK, result)
        }
      }
    }
  }
}
