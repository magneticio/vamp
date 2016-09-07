package io.vamp.rest_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.config.Config
import io.vamp.common.http.RestApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.operation.controller.DeploymentApiController
import io.vamp.operation.deployment.DeploymentSynchronizationActor
import io.vamp.operation.gateway.GatewaySynchronizationActor
import io.vamp.operation.sla.{ EscalationActor, SlaActor }
import io.vamp.operation.workflow.WorkflowSynchronizationActor
import io.vamp.persistence.db.ArtifactPaginationSupport
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

trait DeploymentApiRoute extends DeploymentApiController with SystemController with DevController {
  this: ArtifactPaginationSupport with ExecutionContextProvider with ActorSystemProvider with RestApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  private def asBlueprint = parameters('as_blueprint.as[Boolean] ? false)

  private val helperRoutes = pathPrefix("sync") {
    onComplete(sync()) { _ ⇒
      complete(Accepted)
    }
  } ~ path("sla") {
    onComplete(slaCheck()) { _ ⇒
      complete(Accepted)
    }
  } ~ path("escalation") {
    onComplete(slaEscalation()) { _ ⇒
      complete(Accepted)
    }
  } ~ path("haproxy") {
    onSuccess(haproxy()) { result ⇒
      respondWith(OK, result)
    }
  } ~ pathPrefix("configuration" | "config") {
    get {
      path(Segment) { key: String ⇒
        pathEndOrSingleSlash {
          onSuccess(configuration(key)) { result ⇒
            respondWith(OK, result)
          }
        }
      } ~ pathEndOrSingleSlash {
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
        } ~ put {
          entity(as[String]) { request ⇒
            onSuccess(scaleUpdate(deployment, cluster, breed, request)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    }

  val deploymentRoutes = helperRoutes ~ deploymentRoute ~ slaRoute ~ scaleRoute
}

trait SystemController {
  this: ArtifactPaginationSupport with NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def haproxy(): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(HaProxyGatewayMarshaller.path) map {
      case Some(result: String) ⇒ HttpEntity(result)
      case _                    ⇒ HttpEntity("")
    }
  }

  def configuration(key: String = "") = Future.successful {
    val entries = Config.entries().filter {
      case (k, _) ⇒ k.startsWith("vamp.")
    }
    if (key.nonEmpty) entries.get(key) else entries
  }
}

trait DevController {
  this: ArtifactPaginationSupport with NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def sync() = Future.successful {
    IoC.actorFor[DeploymentSynchronizationActor] ! DeploymentSynchronizationActor.SynchronizeAll
    Thread.sleep(1000)
    IoC.actorFor[GatewaySynchronizationActor] ! GatewaySynchronizationActor.SynchronizeAll
    Thread.sleep(1000)
    IoC.actorFor[WorkflowSynchronizationActor] ! WorkflowSynchronizationActor.SynchronizeAll
  }

  def slaCheck() = Future.successful {
    IoC.actorFor[SlaActor] ! SlaActor.SlaProcessAll
  }

  def slaEscalation() = Future.successful {
    val now = OffsetDateTime.now()
    IoC.actorFor[EscalationActor] ! EscalationActor.EscalationProcessAll(now.minus(1, ChronoUnit.HOURS), now)
  }
}
