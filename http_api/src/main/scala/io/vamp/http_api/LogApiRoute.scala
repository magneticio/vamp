package io.vamp.http_api

import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.{ ConfigMagnet, Namespace }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.LogApiController

import scala.concurrent.duration.FiniteDuration

trait LogApiRoute extends AbstractRoute with LogApiController with EventStreamMarshalling with LazyLogging {
  this: HttpApiDirectives ⇒

  def sseKeepAliveTimeout: ConfigMagnet[FiniteDuration]

  def sseLogRoutes(implicit namespace: Namespace): Route = path("logs" | "log") {
    pathEndOrSingleSlash {
      get {
        parameters('level.as[String] ? "") { level ⇒
          parameters('logger.as[String] ? "") { loggerP ⇒
            logger.info(s"log route called with $level $loggerP")
            complete(sourceLog(level, if (loggerP.trim.isEmpty) None else Option(loggerP), sseKeepAliveTimeout()))
          }
        }
      }
    }
  }
}
