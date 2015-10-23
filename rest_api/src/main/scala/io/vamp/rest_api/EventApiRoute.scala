package io.vamp.rest_api

import akka.actor.ActorRef
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import io.vamp.common.http.SseDirectives._
import io.vamp.model.reader.EventQueryReader
import io.vamp.operation.controller.EventApiController
import spray.http.StatusCodes._

import scala.concurrent.duration._
import scala.language.postfixOps

trait EventApiRoute extends EventApiController {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val sseKeepAliveTimeout = context.system.settings.config.getInt("vamp.rest-api.sse.keep-alive-timeout") seconds

  val eventRoutes = pathPrefix("events") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request ⇒
          onSuccess(publish(request)) { result ⇒
            respondWith(Created, result)
          }
        }
      }
    } ~ path("get") {
      pathEndOrSingleSlash {
        post {
          pageAndPerPage() { (page, perPage) ⇒
            entity(as[String]) { request ⇒
              onSuccess(query(request)(page, perPage)) { response ⇒
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
          sse { channel ⇒ openStream(channel, parameters.getOrElse("tags", Nil).toSet) }
        }
      } ~ post {
        entity(as[String]) { request ⇒
          sse { channel ⇒ openStream(channel, if (request.isEmpty) Set[String]() else EventQueryReader.read(request).tags) }
        }
      }
    }
  }

  override def openStream(channel: ActorRef, tags: Set[String]) = {
    log.debug("SSE connection open.")
    registerClosedHandler(channel, { () ⇒
      closeStream(channel)
      log.debug("SSE connection closed.")
    })
    super.openStream(channel, tags)
  }
}
