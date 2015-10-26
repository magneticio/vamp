package io.vamp.rest_api

import akka.event.Logging._
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.{ CorsSupport, RestApiBase }
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.persistence.ArtifactPaginationSupport
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http._
import spray.routing.directives.LogEntry

import scala.language.{ existentials, postfixOps }

trait RestApiRoute extends RestApiBase with ArtifactApiController with DeploymentApiRoute with EventApiRoute with InfoRoute with ArtifactPaginationSupport with CorsSupport {
  this: CommonSupportForActors ⇒

  implicit def timeout: Timeout

  val crudRoutes = path(Segment) { artifact: String ⇒
    pathEndOrSingleSlash {
      get {
        pageAndPerPage() { (page, perPage) ⇒
          expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
            onSuccess(allArtifacts(artifact, expandReferences, onlyReferences)(page, perPage)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      } ~ post {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(createArtifact(artifact, request, validateOnly)) { result ⇒
              respondWith(Created, result)
            }
          }
        }
      }
    }
  } ~ path(Segment / Segment) { (artifact: String, name: String) ⇒
    pathEndOrSingleSlash {
      get {
        rejectEmptyResponse {
          expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
            onSuccess(readArtifact(artifact, name, expandReferences, onlyReferences)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      } ~ put {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(updateArtifact(artifact, name, request, validateOnly)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      } ~ delete {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(deleteArtifact(artifact, name, request, validateOnly)) { result ⇒
              respondWith(NoContent, None)
            }
          }
        }
      }
    }
  }

  val route = cors {
    noCachingAllowed {
      pathPrefix("api" / "v1") {
        compressResponse() {
          sseRoutes ~ accept(`application/json`, `application/x-yaml`) {
            infoRoute ~ deploymentRoutes ~ eventRoutes ~ crudRoutes
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
