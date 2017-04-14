package io.vamp.http_api

import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.{ ConfigMagnet, Namespace }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.LogApiController

import scala.concurrent.duration.FiniteDuration

trait LogApiRoute extends AbstractRoute with LogApiController with EventStreamMarshalling {
  this: HttpApiDirectives ⇒

  def sseKeepAliveTimeout: ConfigMagnet[FiniteDuration]

  def sseLogRoutes(implicit namespace: Namespace) = path("logs" | "log") {
    pathEndOrSingleSlash {
      get {
        parameters('level.as[String] ? "") { level ⇒
          parameters('logger.as[String] ? "") { logger ⇒
            complete(sourceLog(level, if (logger.trim.isEmpty) None else Option(logger), sseKeepAliveTimeout()))
          }
        }
      }
    }
  }
}
