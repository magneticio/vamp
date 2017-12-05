package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.OK
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.utilcontroller.StatsController

trait StatsRoute extends AbstractRoute with StatsController {
  this: HttpApiDirectives ⇒

  def statsRoute(implicit namespace: Namespace, timeout: Timeout) = pathPrefix("stats" | "statistics") {
    pathEndOrSingleSlash {
      get {
        onSuccess(stats()) { result ⇒
          respondWith(OK, result)
        }
      }
    }
  }
}
