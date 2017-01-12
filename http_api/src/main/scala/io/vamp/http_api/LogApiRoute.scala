package io.vamp.http_api

import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.controller.LogApiController

import scala.concurrent.duration.FiniteDuration

trait LogApiRoute extends LogApiController with EventStreamMarshalling {
  this: ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  def sseKeepAliveTimeout: FiniteDuration

  val sseLogRoutes = path("logs" | "log") {
    pathEndOrSingleSlash {
      get {
        parameters('level.as[String] ? "") { level ⇒
          parameters('logger.as[Option[String]]) { logger ⇒
            complete(sourceLog(level, logger, sseKeepAliveTimeout))
          }
        }
      }
    }
  }
}
