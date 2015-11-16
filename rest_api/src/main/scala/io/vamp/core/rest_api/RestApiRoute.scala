package io.vamp.core.rest_api

import akka.event.Logging._
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import io.vamp.core.operation.controller.ArtifactApiController
import io.vamp.core.persistence.ArtifactPaginationSupport
import shapeless.HNil
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http._
import spray.routing._
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

  override protected def contentTypeOnly(mt: MediaType*): Directive0 = extract(_.request.headers).flatMap[HNil] {
    case headers ⇒ if (headers.exists({
      case `Content-Type`(ContentType(mediaType: MediaType, _)) ⇒ mt.exists(_.value == mediaType.value)
      case _ ⇒ false
    })) pass
    else reject(MalformedHeaderRejection("Content-Type", s"Only the following media types are supported: ${mt.mkString(", ")}"))

  } & cancelAllRejections(ofType[MalformedHeaderRejection])
}

/**
 * @see https://gist.github.com/joseraya/176821d856b43b1cfe19
 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
 */
trait CorsSupport {
  this: HttpServiceBase ⇒

  protected val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)

  protected val optionsCorsHeaders = List(
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
    `Access-Control-Max-Age`(1728000))

  def cors[T]: Directive0 = mapRequestContext { ctx ⇒
    ctx.withRouteResponseHandling({
      // It is an option request for a resource that responds to some other method
      case Rejected(x) if ctx.request.method.equals(HttpMethods.OPTIONS) && x.exists(_.isInstanceOf[MethodRejection]) ⇒
        val allowedMethods: List[HttpMethod] = x.filter(_.isInstanceOf[MethodRejection]).map(rejection ⇒ {
          rejection.asInstanceOf[MethodRejection].supported
        })
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods: _*) :: allowOriginHeader ::
            optionsCorsHeaders
        ))
    }).withHttpResponseHeadersMapped { headers ⇒
      allowOriginHeader :: headers
    }
  }
}