package io.vamp.http_api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.{ Directive0, Route }
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers }
import io.vamp.common.{ Artifact, Config, Namespace }
import io.vamp.http_api.notification.HttpApiNotificationProvider
import io.vamp.http_api.ws.WebSocketRoute
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.controller.ArtifactApiController
import io.vamp.operation.controller.utilcontroller.ComposeApiController
import org.json4s.Formats
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object HttpApiRoute {

  val timeout = Config.timeout("vamp.http-api.response-timeout")

  val stripPathSegments = Config.int("vamp.http-api.strip-path-segments")
}

trait AbstractHttpApiRoute extends HttpApiDirectives with HttpApiHandlers with LogDirective with HttpApiNotificationProvider with AbstractRoute {

  implicit def actorSystem: ActorSystem

  implicit def materializer: Materializer

  implicit lazy val formats: Formats = CoreSerializationFormat.default

  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
}

class HttpApiRoute(implicit val actorSystem: ActorSystem, val materializer: Materializer)
    extends AbstractHttpApiRoute
    with WebSocketRoute
    with UiRoute
    with ArtifactApiController
    with ComposeApiController
    with DeploymentApiRoute
    with WorkflowApiRoute
    with EventApiRoute
    with InfoRoute
    with StatsRoute
    with MetricsRoute
    with HealthRoute
    with JavascriptBreedRoute
    with SystemRoute
    with LogApiRoute
    with ProxyRoute {

  protected def crudRoutes(implicit namespace: Namespace, timeout: Timeout) = {
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
              blueprintName { nameOpt ⇒
                nameOpt.map { name ⇒
                  createBlueprintFromCompose(artifact, name, request).map { blueprintSource ⇒
                    onSuccess(createArtifact("blueprints", blueprintSource, validateOnly)) { _ ⇒
                      complete {
                        HttpResponse(status = Created, entity = blueprintSource)
                      }
                    }
                  }.getOrElse {
                    respondWith(NotFound, None)
                  }
                }.getOrElse {
                  onSuccess(createArtifact(artifact, request, validateOnly)) { result ⇒
                    respondWith(if (background(artifact)) Accepted else Created, result)
                  }
                }
              }
            }
          }
        }
      }
    } ~ pathPrefix(Segment / Remaining) { (artifact, name) ⇒
      pathEndOrSingleSlash {
        get {
          name match {
            case "" ⇒ pageAndPerPage() { (page, perPage) ⇒
              expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
                onSuccess(readArtifacts(artifact, expandReferences, onlyReferences)(page, perPage)) { result ⇒
                  respondWith(OK, result)
                }
              }
            }
            case _ ⇒ rejectEmptyResponse {
              expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
                onSuccess(readArtifact(artifact, name, expandReferences, onlyReferences)) {
                  result ⇒ respondWith(if (result == None) NotFound else OK, result)
                }
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

  protected def websocketApiHandler(implicit namespace: Namespace, timeout: Timeout): Route = {
    infoRoute ~ statsRoute ~ deploymentRoutes ~ workflowStatusRoute ~ eventRoutes ~ metricsRoutes ~ healthRoutes ~ systemRoutes ~ crudRoutes ~ javascriptBreedRoute
  }

  def apiRoutes(implicit namespace: Namespace, timeout: Timeout): Route = {
    pathWithNamespace {
      noCachingAllowed {
        cors() {
          pathPrefix("api" / Artifact.version) {
            encodeResponse {
              sseRoutes ~ sseLogRoutes ~
                accept(`application/json`, HttpApiDirectives.`application/x-yaml`) {
                  infoRoute ~ statsRoute ~ deploymentRoutes ~ workflowStatusRoute ~ eventRoutes ~ metricsRoutes ~ healthRoutes
                } ~ systemRoutes ~ mesosConfigRoute ~
                accept(`application/json`, HttpApiDirectives.`application/x-yaml`) {
                  crudRoutes
                } ~ javascriptBreedRoute
            }
          }
        }
      } ~ pathPrefix("websocket")(websocketRoutes) ~ pathPrefix("proxy")(proxyRoute)
    }
  }

  def allRoutes(implicit namespace: Namespace): Route = {
    implicit val timeout = HttpApiRoute.timeout()
    val stripPathSegments = HttpApiRoute.stripPathSegments()
    log {
      handleExceptions(exceptionHandler) {
        handleRejections(rejectionHandler) {
          withRequestTimeout(timeout.duration) {
            if (stripPathSegments > 0) pathPrefix(Segments(stripPathSegments)) { _ ⇒ apiRoutes ~ uiRoutes } else apiRoutes ~ uiRoutes
          }
        }
      }
    }
  }

  protected def pathWithNamespace(route: Route)(implicit namespace: Namespace): Route = {
    pathPrefix(namespace.name) {
      route
    } ~ route
  }
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
