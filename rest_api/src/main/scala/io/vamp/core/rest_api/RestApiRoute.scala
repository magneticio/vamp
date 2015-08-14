package io.vamp.core.rest_api

import akka.event.Logging._
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import io.vamp.core.operation.controller.ArtifactApiController
import io.vamp.core.rest_api.swagger.SwaggerResponse
import spray.http.HttpRequest
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.routing.directives.LogEntry

import scala.language.{existentials, postfixOps}

trait RestApiRoute extends RestApiBase with ArtifactApiController with DeploymentApiRoute with EventApiRoute with InfoRoute with SwaggerResponse {
  this: CommonSupportForActors =>

  implicit def timeout: Timeout

  val route = pathPrefix("api" / "v1") {
    compressResponse() {
      sseRoutes
    }
  } ~ allowXhrFromOtherHosts {
    noCachingAllowed {
      pathPrefix("api" / "v1") {
        compressResponse() {
          accept(`application/json`, `application/x-yaml`) {
            path("docs") {
              pathEndOrSingleSlash {
                respondWithStatus(OK) {
                  complete(swagger)
                }
              }
            } ~ infoRoute ~ deploymentRoutes ~ eventRoutes ~
              path(Segment) { artifact: String =>
                pathEndOrSingleSlash {
                  get {
                    pageAndPerPage() { (page, perPage) =>
                      expandAndOnlyReferences { (expandReferences, onlyReferences) =>
                        onSuccess(allArtifacts(artifact, expandReferences, onlyReferences)(page, perPage)) { result =>
                          respondWith(OK, result)
                        }
                      }
                    }
                  } ~ post {
                    entity(as[String]) { request =>
                      validateOnly { validateOnly =>
                        onSuccess(createArtifact(artifact, request, validateOnly)) { result =>
                          respondWith(Created, result)
                        }
                      }
                    }
                  }
                }
              } ~ path(Segment / Segment) { (artifact: String, name: String) =>
              pathEndOrSingleSlash {
                get {
                  rejectEmptyResponse {
                    expandAndOnlyReferences { (expandReferences, onlyReferences) =>
                      onSuccess(readArtifact(artifact, name, expandReferences, onlyReferences)) { result =>
                        respondWith(OK, result)
                      }
                    }
                  }
                } ~ put {
                  entity(as[String]) { request =>
                    validateOnly { validateOnly =>
                      onSuccess(updateArtifact(artifact, name, request, validateOnly)) { result =>
                        respondWith(OK, result)
                      }
                    }
                  }
                } ~ delete {
                  entity(as[String]) { request =>
                    validateOnly { validateOnly =>
                      onSuccess(deleteArtifact(artifact, name, request, validateOnly)) { result =>
                        respondWith(NoContent, None)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } ~ path("") {
        logRequest(showRequest _) {
          compressResponse() {
            // serve up static content from a JAR resource
            getFromResource("vamp-ui/index.html")
          }
        }
      } ~ pathPrefix("") {
        logRequest(showRequest _) {
          compressResponse() {
            getFromResourceDirectory("vamp-ui")
          }
        }
      }
    }
  }

  def showRequest(request: HttpRequest) = LogEntry(s"${request.uri} - Headers: [${request.headers}]", InfoLevel)
}
