package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.OK
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.StatsController

trait StatsRoute extends StatsController {
  this: HttpApiDirectives with CommonProvider ⇒

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
