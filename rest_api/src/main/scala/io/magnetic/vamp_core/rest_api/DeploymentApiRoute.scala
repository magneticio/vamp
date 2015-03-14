package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentWatchdogActor}
import io.magnetic.vamp_core.operation.notification.InternalServerError
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.persistence.PersistenceActor.All
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

  val deploymentRoute =
    pathPrefix("sync") {
      complete(OK, sync())
    } ~ pathPrefix("sla") {
      complete(OK, sla())
    } ~ pathPrefix("reset") {
      complete(OK, reset())
    } ~
      pathPrefix("deployments") {
        pathEndOrSingleSlash {
          get {
            onSuccess(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) {
              complete(OK, _)
            }
          } ~ post {
            entity(as[String]) { request =>
              onSuccess(createDeployment(request)) {
                complete(Created, _)
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
                  complete(OK, _)
                }
              }
            } ~ delete {
              entity(as[String]) { request => onSuccess(deleteDeployment(name, request)) {
                _ => complete(NoContent)
              }
              }
            }
          }
        }
      }
}

trait DeploymentApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def sync(): Unit = {
    actorFor(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(1)
    context.system.scheduler.scheduleOnce(5 seconds, new Runnable {
      def run() = actorFor(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(0)
    })
  }

  def sla(): Unit = {
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
}
