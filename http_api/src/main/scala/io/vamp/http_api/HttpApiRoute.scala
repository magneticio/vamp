package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.util.Tuple
import akka.http.scaladsl.server.{ Directive0, PathMatcher, Route }
import akka.stream.Materializer
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.CommonProvider
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers }
import io.vamp.common.{ Artifact, Config, Namespace }
import io.vamp.http_api.notification.HttpApiNotificationProvider
import io.vamp.http_api.ws.WebSocketRoute
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.persistence.ArtifactPaginationSupport
import org.json4s.Formats
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object HttpApiRoute {

  val timeout = Config.timeout("vamp.http-api.response-timeout")

  val stripPathSegments = Config.int("vamp.http-api.strip-path-segments")
}

class HttpApiRoute(implicit val actorSystem: ActorSystem, val materializer: Materializer, val namespace: Namespace)
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
    with LogApiRoute
    with ProxyRoute
    with LogDirective
    with ArtifactPaginationSupport
    with HttpApiNotificationProvider
    with CommonProvider {

  implicit lazy val timeout = HttpApiRoute.timeout()

  implicit val formats: Formats = CoreSerializationFormat.default

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  protected lazy val stripPathSegments = HttpApiRoute.stripPathSegments()

  protected lazy val crudRoutes = {
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
              onSuccess(deleteArtifact(artifact, name, request, validateOnly)) { _ ⇒
                respondWith(if (background(artifact)) Accepted else NoContent, None)
              }
            }
          }
        }
      }
    }
  }

  protected lazy val websocketApiHandler: Route = infoRoute ~ statsRoute ~ deploymentRoutes ~ eventRoutes ~ metricsRoutes ~ healthRoutes ~ systemRoutes ~ crudRoutes ~ javascriptBreedRoute

  lazy val apiRoutes: Route =
    noCachingAllowed {
      cors() {
        pathPrefix(pathMatcherWithNamespace("api" / Artifact.version)) {
          encodeResponse {
            sseRoutes ~ sseLogRoutes ~
              accept(`application/json`, HttpApiDirectives.`application/x-yaml`) {
                infoRoute ~ statsRoute ~ deploymentRoutes ~ eventRoutes ~ metricsRoutes ~ healthRoutes
              } ~ systemRoutes ~
              accept(`application/json`, HttpApiDirectives.`application/x-yaml`) {
                crudRoutes
              } ~ javascriptBreedRoute
          }
        }
      }
    } ~ pathPrefix(pathMatcherWithNamespace("websocket"))(websocketRoutes) ~ pathPrefix(pathMatcherWithNamespace("proxy"))(proxyRoute)

  lazy val allRoutes: Route = log {
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        withRequestTimeout(timeout.duration) {
          if (stripPathSegments > 0) pathPrefix(Segments(stripPathSegments)) { _ ⇒ apiRoutes ~ uiRoutes } else apiRoutes ~ uiRoutes
        }
      }
    }
  }

  protected def pathMatcherWithNamespace[L](pm: PathMatcher[L])(implicit ev: Tuple[L]): PathMatcher[L] = (pm / namespace.name) | pm
}

trait LogDirective {
  this: HttpApiDirectives ⇒

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  def log: Directive0 = {
    extractRequestContext.flatMap { ctx ⇒
      val uri = ctx.request.uri
      val method = ctx.request.method.value
      logger.debug(s"==> $method $uri")
      mapRouteResult { result ⇒
        result match {
          case Complete(response) ⇒ logger.debug(s"<== ${response.status} $method $uri")
          case _                  ⇒
        }
        result
      }
    }
  }
}
