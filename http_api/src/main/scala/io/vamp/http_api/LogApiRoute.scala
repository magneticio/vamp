package io.vamp.http_api

import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.akka.CommonProvider
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.LogApiController

import scala.concurrent.duration.FiniteDuration

trait LogApiRoute extends LogApiController with EventStreamMarshalling {
  this: HttpApiDirectives with CommonProvider ⇒

  implicit def timeout: Timeout

  def sseKeepAliveTimeout: FiniteDuration

  val sseLogRoutes = path("logs" | "log") {
    pathEndOrSingleSlash {
      get {
        parameters('level.as[String] ? "") { level ⇒
          parameters('logger.as[String] ? "") { logger ⇒
            complete(sourceLog(level, if (logger.trim.isEmpty) None else Option(logger), sseKeepAliveTimeout))
          }
        }
      }
    }
  }
}
