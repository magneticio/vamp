package io.vamp.core.rest_api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.common.http.RestApiBase
import io.vamp.core.model.artifact.DeploymentService.{Deployed, ReadyForUndeployment}
import io.vamp.core.model.artifact._
import io.vamp.core.model.conversion.DeploymentConversion._
import io.vamp.core.model.reader._
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor}
import io.vamp.core.operation.notification.InternalServerError
import io.vamp.core.operation.sla.{EscalationActor, SlaActor}
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.persistence.actor.PersistenceActor.All
import io.vamp.core.rest_api.notification.RestApiNotificationProvider
import spray.http.StatusCodes._

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait DeploymentApiRoute extends DeploymentApiController {
  this: Actor with RestApiBase with ExecutionContextProvider =>

  implicit def timeout: Timeout

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
        parameters('as_blueprint.as[Boolean] ? false) { asBlueprint =>
          pageAndPerPage() { (page, perPage) =>
            onSuccess(deployments(asBlueprint, page, perPage)) { result =>
              respondWith(OK, result)
            }
          }
        }
      } ~ post {
        entity(as[String]) { request =>
          onSuccess(createDeployment(request)) { result =>
            respondWith(Accepted, result)
          }
        }
      }
    } ~ path(Segment) { name: String =>
      pathEndOrSingleSlash {
        get {
          rejectEmptyResponse {
            parameters('as_blueprint.as[Boolean] ? false) { asBlueprint =>
              onSuccess(deployment(name, asBlueprint)) { result =>
                respondWith(OK, result)
              }
            }
          }
        } ~ put {
          entity(as[String]) { request =>
            onSuccess(updateDeployment(name, request)) { result =>
              respondWith(Accepted, result)
            }
          }
        } ~ delete {
          entity(as[String]) { request =>
            onSuccess(deleteDeployment(name, request)) { result =>
            respondWith(Accepted, result)
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

trait DeploymentApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

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
        case any => error(InternalServerError(any))
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
      case any => error(InternalServerError(any))
    }
  }

  def deployments(asBlueprint: Boolean, page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.AllPaginated(classOf[Deployment], page, perPage)).map {
    case list: List[_] => list.map {
      case deployment: Deployment => if (asBlueprint) deployment.asBlueprint else deployment
      case any => any
    }
    case any => any
  }

  def deployment(name: String, asBlueprint: Boolean)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])).map {
    case Some(deployment: Deployment) => if (asBlueprint) deployment.asBlueprint else deployment
    case any => any
  }

  def createDeployment(request: String)(implicit timeout: Timeout) = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint, request)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint, request)
  }

  def updateDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint, request)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint, request)
  }

  def deleteDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = {
    if (request.nonEmpty)
      actorFor(DeploymentActor) ? DeploymentActor.Slice(name, DeploymentBlueprintReader.readReferenceFromSource(request), request)
    else
      actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])
  }

  def sla(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request))
        }
      case _ => None
    }

  def slaDelete(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateSla(deployment, cluster, None, ""))
        }
      case _ => None
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => cluster.services.find(_.breed.name == breedName) match {
            case None => None
            case Some(service) =>
              val scale = ScaleReader.read(request) match {
                case s: ScaleReference => offload(actorFor(PersistenceActor) ? PersistenceActor.Read(s.name, classOf[Scale])).asInstanceOf[DefaultScale]
                case s: DefaultScale => s
              }
              offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request))
          }
        }
      case _ => None
    }

  def routing(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.routing)))
    }

  def routingUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => cluster.services.find(_.breed.name == breedName) match {
            case None => None
            case Some(service) =>
              val routing = RoutingReader.read(request) match {
                case r: RoutingReference => offload(actorFor(PersistenceActor) ? PersistenceActor.Read(r.name, classOf[Routing])).asInstanceOf[DefaultRouting]
                case r: DefaultRouting => r
              }
              offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateRouting(deployment, cluster, service, routing, request))
          }
        }
      case _ => None
    }
}
