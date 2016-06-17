package io.vamp.rest_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, CommonSupportForActors, ExecutionContextProvider, IoC }
import io.vamp.common.config.Config
import io.vamp.common.http.RestApiBase
import io.vamp.common.notification.NotificationProvider
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.KibanaDashboardActor
import io.vamp.model.artifact.DeploymentService.State.Intention._
import io.vamp.model.artifact.{ Deployment, Gateway }
import io.vamp.operation.controller.DeploymentApiController
import io.vamp.operation.deployment.DeploymentSynchronizationActor
import io.vamp.operation.gateway.GatewaySynchronizationActor
import io.vamp.operation.sla.{ EscalationActor, SlaActor }
import io.vamp.operation.workflow.WorkflowSynchronizationActor
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.persistence.operation.DeploymentPersistence._
import io.vamp.persistence.operation.DeploymentServiceState
import spray.http.StatusCodes._
import spray.http._

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }

trait DeploymentApiRoute extends DeploymentApiController with DevController {
  this: ArtifactPaginationSupport with CommonSupportForActors with RestApiBase ⇒

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
  } ~ path("kibana") {
    respondWithStatus(Accepted) {
      complete(kibana())
    }
  } ~ path("haproxy") {
    onSuccess(haproxy()) { result ⇒
      respondWith(OK, result)
    }
  } ~ pathPrefix("configuration" | "config") {
    pathEndOrSingleSlash {
      get {
        onSuccess(configuration()) { result ⇒
          respondWith(OK, result)
        }
      }
    }
  }

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
            onSuccess(slaUpdate(deployment, cluster, request)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        } ~ delete {
          onSuccess(slaDelete(deployment, cluster)) { result ⇒
            respondWith(NoContent, None)
          }
        }
      }
    }

  private val scaleRoute =
    path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "scale") { (deployment: String, cluster: String, breed: String) ⇒
      pathEndOrSingleSlash {
        get {
          onSuccess(scale(deployment, cluster, breed)) { result ⇒
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request ⇒
            onSuccess(scaleUpdate(deployment, cluster, breed, request)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    }

  private val routingRoute =
    path("deployments" / Segment / "clusters" / Segment / "routing") { (deployment: String, cluster: String) ⇒
      pathEndOrSingleSlash {
        get {
          onSuccess(routing(deployment, cluster)) { result ⇒
            respondWith(OK, result)
          }
        } ~ (post | put) {
          entity(as[String]) { request ⇒
            onSuccess(routingUpdate(deployment, cluster, request)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      }
    }

  val deploymentRoutes = helperRoutes ~ deploymentRoute ~ slaRoute ~ scaleRoute ~ routingRoute
}

trait DevController {
  this: ArtifactPaginationSupport with NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def sync(): Unit = Future {
    IoC.actorFor[DeploymentSynchronizationActor] ! DeploymentSynchronizationActor.SynchronizeAll
    Thread.sleep(1000)
    IoC.actorFor[GatewaySynchronizationActor] ! GatewaySynchronizationActor.SynchronizeAll
    Thread.sleep(1000)
    IoC.actorFor[WorkflowSynchronizationActor] ! WorkflowSynchronizationActor.SynchronizeAll
  }

  def slaCheck() = IoC.actorFor[SlaActor] ! SlaActor.SlaProcessAll

  def slaEscalation() = {
    val now = OffsetDateTime.now()
    IoC.actorFor[EscalationActor] ! EscalationActor.EscalationProcessAll(now.minus(1, ChronoUnit.HOURS), now)
  }

  def reset()(implicit timeout: Timeout): Unit = {
    allArtifacts[Deployment] map { deployments ⇒
      Future.sequence {
        deployments.flatMap { deployment ⇒
          deployment.clusters.flatMap { cluster ⇒
            cluster.services.map { service ⇒
              IoC.actorFor[PersistenceActor] ? PersistenceActor.Update(DeploymentServiceState(serviceArtifactName(deployment, cluster, service), Undeploy))
            }
          }
        }
      } onComplete {
        case _ ⇒
          allArtifacts[Gateway] map { gateways ⇒
            Future.sequence {
              gateways.map { gateway ⇒
                IoC.actorFor[PersistenceActor] ? PersistenceActor.Delete(gateway.name, gateway.getClass)
              }
            }
          }
      }
    }
  }

  def kibana(): Unit = IoC.actorFor[KibanaDashboardActor] ! KibanaDashboardActor.KibanaUpdate

  def haproxy(): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(HaProxyGatewayMarshaller.path) map {
      case Some(result: String) ⇒ HttpEntity(result)
      case _                    ⇒ HttpEntity("")
    }
  }

  def configuration() = Future.successful {
    Config.entries().filter {
      case (key, _) ⇒ key.startsWith("vamp.")
    }
  }
}
