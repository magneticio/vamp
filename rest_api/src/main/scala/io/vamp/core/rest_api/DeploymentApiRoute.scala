package io.vamp.core.rest_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorSupport, CommonSupportForActors, ExecutionContextProvider, FutureSupport}
import io.vamp.common.http.RestApiBase
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.artifact.DeploymentService.{Deployed, ReadyForUndeployment}
import io.vamp.core.operation.controller.DeploymentApiController
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.core.operation.notification.InternalServerError
import io.vamp.core.operation.sla.{EscalationActor, SlaActor}
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.persistence.PersistenceActor.All
import spray.http.StatusCodes._

import scala.language.{existentials, postfixOps}

trait DeploymentApiRoute extends DeploymentApiController with DevController {
  this: CommonSupportForActors with RestApiBase =>

  implicit def timeout: Timeout

  private def asBlueprint = parameters('as_blueprint.as[Boolean] ? false)

  private val helperRoutes = pathPrefix("sync") {
    parameters('rate.as[Int] ?) { rate =>
      respondWithStatus(Accepted) {
        complete(sync(rate))
      }
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
  this: NotificationProvider with ActorSupport with FutureSupport with ExecutionContextProvider =>

  def sync(rate: Option[Int])(implicit timeout: Timeout): Unit = rate match {
    case Some(r) =>
      for (i <- 0 until r) {
        actorFor(DeploymentSynchronizationActor) ! SynchronizeAll
      }

    case None =>
      offload(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
        case deployments: List[_] =>
          deployments.asInstanceOf[List[Deployment]].map({ deployment =>
            deployment.clusters.filter(cluster => cluster.services.exists(!_.state.isInstanceOf[Deployed])).count(_ => true)
          }).reduceOption(_ max _).foreach(max => sync(Some(max * 3 + 1)))
        case any => throwException(InternalServerError(any))
      }
  }

  def slaCheck() = {
    actorFor(SlaActor) ! SlaActor.SlaProcessAll
  }

  def slaEscalation() = {
    val now = OffsetDateTime.now()
    actorFor(EscalationActor) ! EscalationActor.EscalationProcessAll(now.minus(1, ChronoUnit.HOURS), now)
  }

  def reset()(implicit timeout: Timeout): Unit = {
    offload(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
      case deployments: List[_] =>
        deployments.asInstanceOf[List[Deployment]].map({ deployment =>
          actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(state = ReadyForUndeployment()))))))
          deployment.clusters.count(_ => true)
        }).reduceOption(_ max _).foreach(max => sync(Some(max * 3)))
      case any => throwException(InternalServerError(any))
    }
  }
}

