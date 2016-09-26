package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.stream.Materializer
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers }
import io.vamp.http_api.notification.HttpApiNotificationProvider
import io.vamp.http_api.ws.WebSocketRoute
import io.vamp.model.artifact.Artifact
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.persistence.db.ArtifactPaginationSupport
import org.json4s.Formats

import scala.concurrent.ExecutionContext

object HttpApiRoute {

  val timeout = Config.timeout("vamp.http-api.response-timeout")

  val stripPathSegments = Config.int("vamp.http-api.strip-path-segments")
}

class HttpApiRoute(implicit val actorSystem: ActorSystem, m: Materializer)
    extends HttpApiDirectives
    with HttpApiHandlers
    with WebSocketRoute
    with UiRoute
    with ArtifactApiController
    with DeploymentApiRoute
    with EventApiRoute
    with InfoRoute
    with StatsRoute
    with MetricsRoute
    with HealthRoute
    with JavascriptBreedRoute
    with SystemRoute
    with DebugRoute
    with ArtifactPaginationSupport
    with ExecutionContextProvider
    with ActorSystemProvider
    with HttpApiNotificationProvider {

  implicit val materializer = m

  implicit val timeout = HttpApiRoute.timeout

  implicit val formats: Formats = CoreSerializationFormat.default

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  val crudRoutes = {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(createArtifacts(request, validateOnly)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      } ~ put {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(updateArtifacts(request, validateOnly)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      } ~ delete {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(deleteArtifacts(request, validateOnly)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    } ~ path(Segment) { artifact: String ⇒
      pathEndOrSingleSlash {
        get {
          pageAndPerPage() { (page, perPage) ⇒
            expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
              onSuccess(readArtifacts(artifact, expandReferences, onlyReferences)(page, perPage)) { result ⇒
                respondWith(OK, result)
              }
            }
          }
        } ~ post {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(createArtifact(artifact, request, validateOnly)) { result ⇒
                respondWith(if (background(artifact)) Accepted else Created, result)
              }
            }
          }
        }
      }
    } ~ pathPrefix(Segment / Remaining) { (artifact, name) ⇒
      pathEndOrSingleSlash {
        get {
          rejectEmptyResponse {
            expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
              onSuccess(readArtifact(artifact, name, expandReferences, onlyReferences)) {
                result ⇒ respondWith(if (result == None) NotFound else OK, result)
              }
            }
          }
        } ~ put {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(updateArtifact(artifact, name, request, validateOnly)) { result ⇒
                respondWith(if (background(artifact)) Accepted else OK, result)
              }
            }
          }
        } ~ delete {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(deleteArtifact(artifact, name, request, validateOnly)) { result ⇒
                respondWith(if (background(artifact)) Accepted else NoContent, None)
              }
            }
          }
        }
      }
    }
  }

  val restfulRoutes = infoRoute ~ statsRoute ~ deploymentRoutes ~ eventRoutes ~ metricsRoutes ~ healthRoutes ~ crudRoutes ~ systemRoutes ~ debugRoutes ~ javascriptBreedRoute

  val apiRoutes = noCachingAllowed {
    cors() {
      pathPrefix("api" / Artifact.version) {
        encodeResponse {
          sseRoutes ~ accept(`application/json`, HttpApiDirectives.`application/x-yaml`) {
            restfulRoutes
          }
        }
      }
    }
  } ~ path("websocket")(websocketRoutes) ~ uiRoutes

  val allRoutes = {
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        withRequestTimeout(timeout.duration) {
          if (HttpApiRoute.stripPathSegments > 0) pathPrefix(Segments(HttpApiRoute.stripPathSegments)) { _ ⇒ apiRoutes } else apiRoutes
        }
      }
    }
  }
}
