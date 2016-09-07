package io.vamp.rest_api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.http.{ RestApiDirectives, RestApiHandlers }
import io.vamp.model.artifact.Artifact
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.persistence.db.ArtifactPaginationSupport
import io.vamp.rest_api.notification.RestApiNotificationProvider
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.ExecutionContext

object RestApiRoute {
  val timeout = Config.timeout("vamp.rest-api.response-timeout")
}

class RestApiRoute(implicit val actorSystem: ActorSystem)
    extends RestApiDirectives
    with RestApiHandlers
    with UiRoute
    with ArtifactApiController
    with DeploymentApiRoute
    with EventApiRoute
    with InfoRoute
    with StatsRoute
    with MetricsRoute
    with HealthRoute
    with JavascriptBreedRoute
    with ArtifactPaginationSupport
    with ExecutionContextProvider
    with ActorSystemProvider
    with RestApiNotificationProvider {

  implicit val timeout = RestApiRoute.timeout

  implicit val formats: Formats = DefaultFormats

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

  val routes = //cors {
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        withRequestTimeout(timeout.duration) {
          noCachingAllowed {
            pathPrefix("api" / Artifact.version) {
              encodeResponse {
                /*sseRoutes ~*/ accept(`application/json`, `application/x-yaml`) {
                  infoRoute ~ statsRoute ~ deploymentRoutes ~ eventRoutes ~ metricsRoutes ~ healthRoutes ~ crudRoutes ~ javascriptBreedRoute
                }
              }
            } ~ uiRoutes
          }
        }
      }
    }
  //}
}
