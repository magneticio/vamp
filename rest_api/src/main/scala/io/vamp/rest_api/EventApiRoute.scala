package io.vamp.rest_api

import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.http.RestApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.controller.EventApiController

trait EventApiRoute extends EventApiController with EventStreamMarshalling {
  this: ExecutionContextProvider with ActorSystemProvider with RestApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  val sseKeepAliveTimeout = Config.duration("vamp.rest-api.sse.keep-alive-timeout")

  val eventRoutes = pathPrefix("events") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request ⇒
          onSuccess(publish(request)) { result ⇒
            respondWith(Created, result)
          }
        }
      } ~ get {
        pageAndPerPage() { (page, perPage) ⇒
          parameterMultiMap { parameters ⇒
            entity(as[String]) { request ⇒
              onSuccess(query(parameters, request)(page, perPage)) { response ⇒
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
            complete(source(parameters, request, sseKeepAliveTimeout))
          }
        }
      }
    }
  }
}
