package io.vamp.http_api

import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.{ Config, Namespace }
import io.vamp.operation.controller.EventApiController

trait EventApiRoute extends AbstractRoute with EventApiController with EventStreamMarshalling {
  this: HttpApiDirectives ⇒

  lazy val sseKeepAliveTimeout = Config.duration("vamp.http-api.sse.keep-alive-timeout")

  def eventRoutes(implicit namespace: Namespace, timeout: Timeout) = pathPrefix("events") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request ⇒
          onSuccess(publishEvent(request)) { result ⇒
            respondWith(Created, result)
          }
        }
      } ~ get {
        pageAndPerPage() { (page, perPage) ⇒
          parameterMultiMap { parameters ⇒
            entity(as[String]) { request ⇒
              onSuccess(queryEvents(parameters, request)(page, perPage)) { response ⇒
                respondWith(OK, response)
              }
            }
          }
        }
      }
    }
  }

  def sseRoutes(implicit namespace: Namespace, timeout: Timeout) = path("events" / "stream") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          entity(as[String]) { request ⇒
            complete(sourceEvents(parameters, request, sseKeepAliveTimeout()))
          }
        }
      }
    }
  }
}
