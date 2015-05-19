package io.vamp.core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.rest_api.notification.{InconsistentArtifactName, RestApiNotificationProvider, UnexpectedArtifact}
import io.vamp.core.rest_api.swagger.SwaggerResponse
import spray.http.MediaTypes._
import spray.http.StatusCodes._

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait RestApiRoute extends RestApiBase with RestApiController with DeploymentApiRoute with InfoRoute with SwaggerResponse {
  this: Actor with ExecutionContextProvider =>

  implicit def timeout: Timeout

  val route = noCachingAllowed {
    allowXhrFromOtherHosts {
      pathPrefix("api" / "v1") {
        accept(`application/json`, `application/x-yaml`) {
          path("docs") {
            pathEndOrSingleSlash {
              complete(OK, swagger)
            }
          } ~ infoRoute ~ deploymentRoutes ~
            path(Segment) { artifact: String =>
              pathEndOrSingleSlash {
                get {
                  onSuccess(allArtifacts(artifact)) {
                    complete(OK, _)
                  }
                } ~ post {
                  entity(as[String]) { request =>
                    parameters('validate_only.as[Boolean] ? false) { validateOnly =>
                      onSuccess(createArtifact(artifact, request, validateOnly)) {
                        complete(Created, _)
                      }
                    }
                  }
                }
              }
            } ~ path(Segment / Segment) { (artifact: String, name: String) =>
            pathEndOrSingleSlash {
              get {
                rejectEmptyResponse {
                  onSuccess(readArtifact(artifact, name)) {
                    complete(OK, _)
                  }
                }
              } ~ put {
                entity(as[String]) { request =>
                  parameters('validate_only.as[Boolean] ? false) { validateOnly =>
                    onSuccess(updateArtifact(artifact, name, request, validateOnly)) {
                      complete(OK, _)
                    }
                  }
                }
              } ~ delete {
                entity(as[String]) { request => onSuccess(deleteArtifact(artifact, name, request)) {
                  _ => complete(NoContent)
                }
                }
              }
            }
          }
        }
      }
    }
  }
}

trait RestApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def allArtifacts(artifact: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.all
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.asInstanceOf[PersistenceController[Artifact]].create(content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.asInstanceOf[PersistenceController[Artifact]].update(name, content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) =>
      if (content.isEmpty)
        controller.delete(name)
      else
        controller.asInstanceOf[PersistenceController[Artifact]].delete(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  private val mapping: Map[String, PersistenceController[_ <: Artifact]] = Map() +
    ("breeds" -> new PersistenceController[Breed](classOf[Breed], BreedReader)) +
    ("blueprints" -> new PersistenceController[Blueprint](classOf[Blueprint], BlueprintReader)) +
    ("slas" -> new PersistenceController[Sla](classOf[Sla], SlaReader)) +
    ("scales" -> new PersistenceController[Scale](classOf[Scale], ScaleReader)) +
    ("escalations" -> new PersistenceController[Escalation](classOf[Escalation], EscalationReader)) +
    ("routings" -> new PersistenceController[Routing](classOf[Routing], RoutingReader)) +
    ("filters" -> new PersistenceController[Filter](classOf[Filter], FilterReader))

  private class PersistenceController[T <: Artifact](`type`: Class[_ <: Artifact], unmarshaller: YamlReader[T]) {

    def all(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.All(`type`)

    def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = unmarshaller.read(source)
      if(validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Create(artifact, Some(source))
    }

    def read(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`)

    def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = unmarshaller.read(source)
      if (name != artifact.name)
        error(InconsistentArtifactName(name, artifact))

      if(validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Update(artifact, Some(source))
    }

    def delete(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Delete(name, `type`)
  }

}
