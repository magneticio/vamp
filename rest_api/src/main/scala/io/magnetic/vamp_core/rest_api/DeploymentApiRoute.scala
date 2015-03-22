package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core.model.artifact.DeploymentService.{ReadyForDeployment, ReadyForUndeployment}
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.conversion.DeploymentConversion._
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor}
import io.magnetic.vamp_core.operation.notification.InternalServerError
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor
import io.magnetic.vamp_core.rest_api.notification.{RestApiNotificationProvider, UnsupportedRoutingWeightChangeError}
import io.magnetic.vamp_core.rest_api.swagger.SwaggerResponse
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.persistence.actor.PersistenceActor.All
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}

trait DeploymentApiRoute extends HttpServiceBase with DeploymentApiController with SwaggerResponse {
  this: Actor with ExecutionContextProvider =>

  implicit def marshaller: Marshaller[Any]

  implicit def timeout: Timeout

  private val helperRoutes = pathPrefix("sync") {
    parameters('rate.as[Int] ? 10) { period =>
      complete(OK, sync(period))
    }
  } ~ pathPrefix("sla") {
    complete(OK, slaMonitor())
  } ~ pathPrefix("reset") {
    complete(OK, reset())
  }


  private val deploymentRoute = pathPrefix("deployments") {
    pathEndOrSingleSlash {
      get {
        parameters('as_blueprint.as[Boolean] ? false) { asBlueprint =>
          onSuccess(deployments(asBlueprint)) {
            complete(OK, _)
          }
        }
      } ~ post {
        entity(as[String]) { request =>
          onSuccess(createDeployment(request)) {
            complete(Accepted, _)
          }
        }
      }
    } ~ path(Segment) { name: String =>
      pathEndOrSingleSlash {
        get {
          rejectEmptyResponse {
            parameters('as_blueprint.as[Boolean] ? false) { asBlueprint =>
              onSuccess(deployment(name, asBlueprint)) {
                complete(OK, _)
              }
            }
          }
        } ~ put {
          entity(as[String]) { request =>
            onSuccess(updateDeployment(name, request)) {
              complete(Accepted, _)
            }
          }
        } ~ delete {
          entity(as[String]) { request => onSuccess(deleteDeployment(name, request)) {
            complete(Accepted, _)
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
          onSuccess(sla(deployment, cluster)) {
            complete(OK, _)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(slaUpdate(deployment, cluster, request)) {
              complete(OK, _)
            }
          }
        } ~ delete {
          onSuccess(slaDelete(deployment, cluster)) {
            _ => complete(NoContent)
          }
        }
      }
    }

  private val scaleRoute =
    path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "scale") { (deployment: String, cluster: String, breed: String) =>
      pathEndOrSingleSlash {
        get {
          onSuccess(scale(deployment, cluster, breed)) {
            complete(OK, _)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(scaleUpdate(deployment, cluster, breed, request)) {
              complete(OK, _)
            }
          }
        }
      }
    }

  private val routingRoute =
    path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "routing") { (deployment: String, cluster: String, breed: String) =>
      pathEndOrSingleSlash {
        get {
          onSuccess(routing(deployment, cluster, breed)) {
            complete(OK, _)
          }
        } ~ (post | put) {
          entity(as[String]) { request =>
            onSuccess(routingUpdate(deployment, cluster, breed, request)) {
              complete(OK, _)
            }
          }
        }
      }
    }

  val deploymentRoutes = helperRoutes ~ deploymentRoute ~ slaRoute ~ scaleRoute ~ routingRoute
}

trait DeploymentApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def sync(rate: Int): Unit = {
    for (i <- 0 until rate) {
      actorFor(DeploymentSynchronizationActor) ! SynchronizeAll
    }
  }

  def slaMonitor(period: Int = 10): Unit = {
    actorFor(SlaMonitorActor) ! SlaMonitorActor.Period(1)
    context.system.scheduler.scheduleOnce(period seconds, new Runnable {
      def run() = actorFor(SlaMonitorActor) ! SlaMonitorActor.Period(0)
    })
  }

  def reset()(implicit timeout: Timeout): Unit = {
    offLoad(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
      case deployments: List[_] =>
        deployments.asInstanceOf[List[Deployment]].map({ deployment =>
          actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(state = ReadyForUndeployment()))))))
          deployment.clusters.count(_ => true)
        }).reduceOption(_ max _).foreach(max => sync(max * 3))
      case any => error(InternalServerError(any))
    }
  }

  def deployments(asBlueprint: Boolean)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])).map {
    case list: List[_] => list.map {
      case deployment: Deployment => if (asBlueprint) deployment.asBlueprint else deployment
      case any => any
    }
    case any => any
  }

  def deployment(name: String, asBlueprint: Boolean)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])).map {
    case deployment: Deployment => if (asBlueprint) deployment.asBlueprint else deployment
    case any => any
  }

  def createDeployment(request: String)(implicit timeout: Timeout) = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint)
  }

  def updateDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint)
  }

  def deleteDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = {
    if (request.nonEmpty)
      actorFor(DeploymentActor) ? DeploymentActor.Slice(name, DeploymentBlueprintReader.readReferenceFromSource(request))
    else
      actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])
  }

  def sla(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap { _ =>
          val sla = Some(SlaReader.read(request))
          actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => if (cluster.name == clusterName) cluster.copy(sla = sla) else cluster)))
          sla
        }
      }
    }

  def slaDelete(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap { _ =>
          actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => if (cluster.name == clusterName) cluster.copy(sla = None) else cluster)))
          None
        }
      }
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap {
          cluster => cluster.services.find(_.breed.name == breedName).flatMap {
            service =>
              val scale = ScaleReader.read(request) match {
                case s: ScaleReference => offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(s.name, classOf[Scale])).asInstanceOf[DefaultScale]
                case s: DefaultScale => s
              }
              actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(scale = Some(scale), state = ReadyForDeployment()))))))
              Some(scale)
          }
        }
      }
    }

  def routing(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.routing)))
    }

  def routingUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap {
          cluster => cluster.services.find(_.breed.name == breedName).flatMap {
            service =>
              val routing = RoutingReader.read(request) match {
                case r: RoutingReference => offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(r.name, classOf[Routing])).asInstanceOf[DefaultRouting]
                case r: DefaultRouting => r
              }

              routing.weight match {
                case Some(w) if w != service.routing.getOrElse(DefaultRouting("", Some(0), Nil)).weight.getOrElse(0) => error(UnsupportedRoutingWeightChangeError(service.routing.getOrElse(DefaultRouting("", Some(0), Nil)).weight.getOrElse(0)))
                case _ =>
              }

              actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(routing = Some(routing), state = ReadyForDeployment()))))))
              Some(routing)
          }
        }
      }
    }
}
