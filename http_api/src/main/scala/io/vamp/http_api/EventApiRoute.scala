package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.Config
import io.vamp.common.akka.CommonProvider
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.EventApiController

trait EventApiRoute extends EventApiController with EventStreamMarshalling {
  this: HttpApiDirectives with CommonProvider ⇒

  implicit def timeout: Timeout

  lazy val sseKeepAliveTimeout = Config.duration("vamp.http-api.sse.keep-alive-timeout")()

  val eventRoutes = pathPrefix("events") {
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

  val sseRoutes = path("events" / "stream") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          entity(as[String]) { request ⇒
            complete(sourceEvents(parameters, request, sseKeepAliveTimeout))
          }
        }
      }
    }
  }
}
