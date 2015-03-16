package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.magnetic.vamp_core.model.artifact.DeploymentService.ReadyForDeployment
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentWatchdogActor}
import io.magnetic.vamp_core.operation.notification.InternalServerError
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor
import io.magnetic.vamp_core.persistence.actor.PersistenceActor
import PersistenceActor.All
import io.magnetic.vamp_core.persistence.actor.PersistenceActor
import io.magnetic.vamp_core.rest_api.notification.RestApiNotificationProvider
import io.magnetic.vamp_core.rest_api.swagger.SwaggerResponse
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
    complete(OK, sync())
  } ~ pathPrefix("sla") {
    complete(OK, slaMonitor())
  } ~ pathPrefix("reset") {
    complete(OK, reset())
  }


  private val deploymentRoute = pathPrefix("deployments") {
    pathEndOrSingleSlash {
      get {
        onSuccess(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) {
          complete(OK, _)
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
            onSuccess(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])) {
              complete(OK, _)
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
            _ => complete(Accepted)
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
        } ~ delete {
          onSuccess(routingDelete(deployment, cluster)) {
            _ => complete(NoContent)
          }
        }
      }
    }

  val deploymentRoutes = helperRoutes ~ deploymentRoute ~ slaRoute ~ scaleRoute ~ routingRoute
}

trait DeploymentApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def sync(): Unit = {
    actorFor(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(1)
    context.system.scheduler.scheduleOnce(5 seconds, new Runnable {
      def run() = actorFor(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(0)
    })
  }

  def slaMonitor(): Unit = {
    actorFor(SlaMonitorActor) ! SlaMonitorActor.Period(1)
    context.system.scheduler.scheduleOnce(5 seconds, new Runnable {
      def run() = actorFor(SlaMonitorActor) ! SlaMonitorActor.Period(0)
    })
  }

  def reset(): Unit = {
    implicit val timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
      case deployments: List[_] =>
        deployments.asInstanceOf[List[Deployment]].foreach(deployment => actorFor(DeploymentActor) ! DeploymentActor.Delete(deployment.name, None))
      case any => error(InternalServerError(any))
    }
    sync()
  }

  def createDeployment(request: String)(implicit timeout: Timeout) = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint)
  }


  def updateDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Update(name, blueprint)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Update(name, blueprint)
  }

  def deleteDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] =
    actorFor(DeploymentActor) ? DeploymentActor.Delete(name, if (request.isEmpty) None else Some(DeploymentBlueprintReader.readReferenceFromSource(request)))

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
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(_.scale))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap {
          cluster => cluster.services.find(_.breed.name == breedName).flatMap {
            service =>
              val scale = Some(ScaleReader.read(request) match {
                case s: ScaleReference => offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(s.name, classOf[Scale])).asInstanceOf[DefaultScale]
                case s: DefaultScale => s
              })
              actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(scale = scale, state = ReadyForDeployment()))))))
              scale
          }
        }
      }
    }

  def routing(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(_.routing))
    }

  def routingUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap {
          cluster => cluster.services.find(_.breed.name == breedName).flatMap {
            service =>
              val routing = Some(RoutingReader.read(request) match {
                case r: RoutingReference => offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(r.name, classOf[Routing])).asInstanceOf[DefaultRouting]
                case r: DefaultRouting => r
              })
              actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(routing = routing, state = ReadyForDeployment()))))))
              routing
          }
        }
      }
    }

  def routingDelete(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap {
        deployment => deployment.clusters.find(_.name == clusterName).flatMap { _ =>
          actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map(service => service.copy(routing = None, state = ReadyForDeployment()))))))
          None
        }
      }
    }
}
