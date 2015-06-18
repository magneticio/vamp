package io.vamp.core.rest_api

import _root_.io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import _root_.io.vamp.common.http.RestApiBase
import _root_.io.vamp.core.model.artifact._
import _root_.io.vamp.core.model.reader._
import _root_.io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import _root_.io.vamp.core.operation.workflow.WorkflowSchedulerActor
import _root_.io.vamp.core.persistence.actor.PersistenceActor
import _root_.io.vamp.core.rest_api.notification.{InconsistentArtifactName, RestApiNotificationProvider, UnexpectedArtifact}
import _root_.io.vamp.core.rest_api.swagger.SwaggerResponse
import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import spray.http.HttpRequest
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.routing.directives.LogEntry
import akka.event.Logging._

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.reflect._

trait RestApiRoute extends RestApiBase with RestApiController with DeploymentApiRoute with InfoRoute with SwaggerResponse {
  this: Actor with ExecutionContextProvider =>

  implicit def timeout: Timeout

  val route = noCachingAllowed {
    allowXhrFromOtherHosts {
      pathPrefix("api" / "v1") {
        accept(`application/json`, `application/x-yaml`) {
          path("docs") {
            pathEndOrSingleSlash {
              respondWithStatus(OK) {
                complete(swagger)
              }
            }
          } ~ infoRoute ~ deploymentRoutes ~
            path(Segment) { artifact: String =>
              pathEndOrSingleSlash {
                get {
                  pageAndPerPage() { (page, perPage) =>
                    onSuccess(allArtifacts(artifact, page, perPage)) { result =>
                      respondWith(OK, result)
                    }
                  }
                } ~ post {
                  entity(as[String]) { request =>
                    parameters('validate_only.as[Boolean] ? false) { validateOnly =>
                      onSuccess(createArtifact(artifact, request, validateOnly)) { result =>
                        respondWith(Created, result)
                      }
                    }
                  }
                }
              }
            } ~ path(Segment / Segment) { (artifact: String, name: String) =>
            pathEndOrSingleSlash {
              get {
                rejectEmptyResponse {
                  onSuccess(readArtifact(artifact, name)) { result =>
                    respondWith(OK, result)
                  }
                }
              } ~ put {
                entity(as[String]) { request =>
                  parameters('validate_only.as[Boolean] ? false) { validateOnly =>
                    onSuccess(updateArtifact(artifact, name, request, validateOnly)) { result =>
                      respondWith(OK, result)
                    }
                  }
                }
              } ~ delete {
                entity(as[String]) { request =>
                  parameters('validate_only.as[Boolean] ? false) { validateOnly =>
                    onSuccess(deleteArtifact(artifact, name, request, validateOnly)) { result =>
                      respondWith(NoContent, None)
                    }
                  }
                }
              }
            }
          }
        }
      } ~ path("") {
        logRequest(showRequest _) {
          compressResponse() {
            // serve up static content from a JAR resource
            getFromResource("vamp-ui/index.html")
          }
        }
      } ~ pathPrefix(""){
        logRequest(showRequest _) {
          compressResponse() {
              getFromResourceDirectory("vamp-ui")
          }
        }
      }
    }
  }

  def showRequest(request: HttpRequest) = LogEntry(s"${request.uri} - Headers: [${request.headers}]", InfoLevel)
}

trait RestApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def allArtifacts(artifact: String, page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.all(page, perPage)
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.create(content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.update(name, content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.delete(name, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  private val mapping: Map[String, Controller] = Map() +
    ("breeds" -> new PersistenceController[Breed](BreedReader)) +
    ("blueprints" -> new PersistenceController[Blueprint](BlueprintReader)) +
    ("slas" -> new PersistenceController[Sla](SlaReader)) +
    ("scales" -> new PersistenceController[Scale](ScaleReader)) +
    ("escalations" -> new PersistenceController[Escalation](EscalationReader)) +
    ("routings" -> new PersistenceController[Routing](RoutingReader)) +
    ("filters" -> new PersistenceController[Filter](FilterReader)) +
    ("workflows" -> new PersistenceController[Workflow](WorkflowReader)) +
    ("scheduled-workflows" -> new ScheduledWorkflowController()) +
    ("deployments" -> new Controller())

  private class Controller {

    def all(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = Future(Nil)

    def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(error(UnexpectedArtifact(source)))

    def read(name: String)(implicit timeout: Timeout): Future[Any] = Future(None)

    def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(error(UnexpectedArtifact(source)))

    def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)
  }

  private class PersistenceController[T <: Artifact : ClassTag](unmarshaller: YamlReader[T]) extends Controller {

    val `type` = classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]

    override def all(page: Int, perPage: Int)(implicit timeout: Timeout) =
      actorFor(PersistenceActor) ? PersistenceActor.AllPaginated(`type`, page, perPage)

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = unmarshaller.read(source)
      if (validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Create(artifact, Some(source))
    }

    override def read(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`)

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = unmarshaller.read(source)
      if (name != artifact.name)
        error(InconsistentArtifactName(name, artifact))

      if (validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Update(artifact, Some(source))
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) =
      if (validateOnly) Future(None) else actorFor(PersistenceActor) ? PersistenceActor.Delete(name, `type`)
  }

  private class ScheduledWorkflowController extends PersistenceController[ScheduledWorkflow](ScheduledWorkflowReader) {

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.create(source, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any => any
    }

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.update(name, source, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any => any
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.delete(name, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Unschedule(workflow)
        workflow
      case any => any
    }
  }

}
