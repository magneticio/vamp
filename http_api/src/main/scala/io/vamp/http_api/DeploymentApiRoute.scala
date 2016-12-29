package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes._
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.controller.DeploymentApiController
import io.vamp.persistence.db.ArtifactPaginationSupport

trait DeploymentApiRoute extends DeploymentApiController {
  this: ArtifactPaginationSupport with ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  private def asBlueprint = parameters('as_blueprint.as[Boolean] ? false)

  private val deploymentRoute = pathPrefix("deployments") {
    pathEndOrSingleSlash {
      get {
        asBlueprint { asBlueprint ⇒
          pageAndPerPage() { (page, perPage) ⇒
            expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
              onSuccess(deployments(asBlueprint, expandReferences, onlyReferences)(page, perPage)) { result ⇒
                respondWith(OK, result)
              }
            }
          }
        }
      } ~ post {
        entity(as[String]) { request ⇒
          validateOnly { validateOnly ⇒
            onSuccess(createDeployment(request, validateOnly)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    } ~ path(Segment) { name: String ⇒
      pathEndOrSingleSlash {
        get {
          rejectEmptyResponse {
            asBlueprint { asBlueprint ⇒
              expandAndOnlyReferences { (expandReferences, onlyReferences) ⇒
                onSuccess(deployment(name, asBlueprint, expandReferences, onlyReferences)) { result ⇒
                  respondWith(OK, result)
                }
              }
            }
          }
        } ~ put {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(updateDeployment(name, request, validateOnly)) { result ⇒
                respondWith(Accepted, result)
              }
            }
          }
        } ~ delete {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(deleteDeployment(name, request, validateOnly)) { result ⇒
                respondWith(Accepted, result)
              }
            }
          }
        }
      }
    }
  }

  private val slaRoute =
    path("deployments" / Segment / "clusters" / Segment / "sla") { (deployment: String, cluster: String) ⇒
      pathEndOrSingleSlash {
        get {
          onSuccess(sla(deployment, cluster)) { result ⇒
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(slaUpdate(deployment, cluster, request, validateOnly)) { result ⇒
                respondWith(Accepted, result)
              }
            }
          }
        } ~ delete {
          validateOnly { validateOnly ⇒
            onSuccess(slaDelete(deployment, cluster, validateOnly)) { result ⇒
              respondWith(NoContent, None)
            }
          }
        }
      }
    }

  private val scaleRoute =
    path("deployments" / Segment / "clusters" / Segment / "META-INF/services" / Segment / "scale") { (deployment: String, cluster: String, breed: String) ⇒
      pathEndOrSingleSlash {
        get {
          onSuccess(scale(deployment, cluster, breed)) { result ⇒
            respondWith(OK, result)
          }
        } ~ put {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(scaleUpdate(deployment, cluster, breed, request, validateOnly)) { result ⇒
                respondWith(Accepted, result)
              }
            }
          }
        }
      }
    }

  val deploymentRoutes = deploymentRoute ~ slaRoute ~ scaleRoute
}
