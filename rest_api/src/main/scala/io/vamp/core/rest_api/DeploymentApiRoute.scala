package io.vamp.core.rest_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.util.Timeout
import io.vamp.common.akka.{ActorSystemProvider, CommonSupportForActors, ExecutionContextProvider, IoC}
import io.vamp.common.http.RestApiBase
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.artifact.DeploymentService.ReadyForUndeployment
import io.vamp.core.operation.controller.DeploymentApiController
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.core.operation.sla.{EscalationActor, SlaActor}
import io.vamp.core.persistence.{ArtifactPaginationSupport, PersistenceActor}
import spray.http.StatusCodes._

import scala.language.{existentials, postfixOps}

trait DeploymentApiRoute extends DeploymentApiController with DevController {
  this: ArtifactPaginationSupport with CommonSupportForActors with RestApiBase =>

  implicit def timeout: Timeout

  private def asBlueprint = parameters('as_blueprint.as[Boolean] ? false)

  private val helperRoutes = pathPrefix("sync") {
    respondWithStatus(Accepted) {
      complete(sync())
    }
  } ~ path("sla") {
    respondWithStatus(Accepted) {
      complete(slaCheck())
    }
  } ~ path("escalation") {
    respondWithStatus(Accepted) {
      complete(slaEscalation())
    }
  } ~ path("reset") {
    respondWithStatus(Accepted) {
      complete(reset())
    }
  }

  private val deploymentRoute = pathPrefix("deployments") {
    pathEndOrSingleSlash {
      get {
        asBlueprint { asBlueprint =>
          pageAndPerPage() { (page, perPage) =>
            expandAndOnlyReferences { (expandReferences, onlyReferences) =>
              onSuccess(deployments(asBlueprint, expandReferences, onlyReferences)(page, perPage)) { result =>
                respondWith(OK, result)
              }
            }
          }
        }
      } ~ post {
        entity(as[String]) { request =>
          validateOnly { validateOnly =>
            onSuccess(createDeployment(request, validateOnly)) { result =>
              respondWith(Accepted, result)
            }
          }
        }
      }
    } ~ path(Segment) { name: String =>
      pathEndOrSingleSlash {
        get {
          rejectEmptyResponse {
            asBlueprint { asBlueprint =>
              expandAndOnlyReferences { (expandReferences, onlyReferences) =>
                onSuccess(deployment(name, asBlueprint, expandReferences, onlyReferences)) { result =>
                  respondWith(OK, result)
                }
              }
            }
          }
        } ~ put {
          entity(as[String]) { request =>
            validateOnly { validateOnly =>
              onSuccess(updateDeployment(name, request, validateOnly)) { result =>
                respondWith(Accepted, result)
              }
            }
          }
        } ~ delete {
          entity(as[String]) { request =>
            validateOnly { validateOnly =>
              onSuccess(deleteDeployment(name, request, validateOnly)) { result =>
                respondWith(Accepted, result)
              }
            }
          }
        }
      }
    }
  }

  private val slaRoute =
    path("deployments" / Segment / "clusters" / Segment / "sla") { (deployment: String, cluster: String) =>
      pathEndOrSingleSlash {
        get {
          onSuccess(sla(deployment, cluster)) { result =>
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(slaUpdate(deployment, cluster, request)) { result =>
              respondWith(Accepted, result)
            }
          }
        } ~ delete {
          onSuccess(slaDelete(deployment, cluster)) { result =>
            respondWith(NoContent, None)
          }
        }
      }
    }

  private val scaleRoute =
    path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "scale") { (deployment: String, cluster: String, breed: String) =>
      pathEndOrSingleSlash {
        get {
          onSuccess(scale(deployment, cluster, breed)) { result =>
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(scaleUpdate(deployment, cluster, breed, request)) { result =>
              respondWith(Accepted, result)
            }
          }
        }
      }
    }

  private val routingRoute =
    path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "routing") { (deployment: String, cluster: String, breed: String) =>
      pathEndOrSingleSlash {
        get {
          onSuccess(routing(deployment, cluster, breed)) { result =>
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(routingUpdate(deployment, cluster, breed, request)) { result =>
              respondWith(OK, result)
            }
          }
        }
      }
    }

  val deploymentRoutes = helperRoutes ~ deploymentRoute ~ slaRoute ~ scaleRoute ~ routingRoute
}

trait DevController {
  this: ArtifactPaginationSupport with NotificationProvider with ExecutionContextProvider with ActorSystemProvider =>

  def sync(): Unit = IoC.actorFor(DeploymentSynchronizationActor) ! SynchronizeAll

  def slaCheck() = IoC.actorFor(SlaActor) ! SlaActor.SlaProcessAll

  def slaEscalation() = {
    val now = OffsetDateTime.now()
    IoC.actorFor(EscalationActor) ! EscalationActor.EscalationProcessAll(now.minus(1, ChronoUnit.HOURS), now)
  }

  def reset()(implicit timeout: Timeout): Unit = allArtifacts(classOf[Deployment]) map { deployments =>
    deployments.foreach { deployment =>
      IoC.actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(state = ReadyForUndeployment()))))))
    }
    sync()
  }
}

