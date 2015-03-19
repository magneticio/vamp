package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.model.serialization.{ArtifactSerializationFormat, BlueprintSerializationFormat, BreedSerializationFormat, DeploymentSerializationFormat, SlaSerializationFormat}
import io.magnetic.vamp_core.persistence.actor.PersistenceActor
import io.magnetic.vamp_core.rest_api.notification.{InconsistentArtifactName, RestApiNotificationProvider, UnexpectedArtifact}
import io.magnetic.vamp_core.rest_api.swagger.SwaggerResponse
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.common.notification.NotificationErrorException
import org.json4s.native.Serialization._
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpEntity
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait RestApiRoute extends HttpServiceBase with RestApiController with DeploymentApiRoute with SwaggerResponse {
  this: Actor with ExecutionContextProvider =>

  implicit def timeout: Timeout

  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  implicit val marshaller: Marshaller[Any] = Marshaller.of[Any](`application/json`) { (value, contentType, ctx) =>
    implicit val formats = ArtifactSerializationFormat(BreedSerializationFormat, BlueprintSerializationFormat, SlaSerializationFormat, DeploymentSerializationFormat)

    val response = value match {
      case notification: NotificationErrorException => throw notification
      case exception: Exception => throw new RuntimeException(exception)
      case response: AnyRef => write(response)
      case any => write(any.toString)
    }
    ctx.marshalTo(HttpEntity(contentType, response))
  }

  val route = noCachingAllowed {
    pathPrefix("api" / "v1") {
      respondWithMediaType(`application/json`) {
        path("docs") {
          pathEndOrSingleSlash {
            complete(OK, swagger)
          }
        } ~ deploymentRoutes ~
          path(Segment) { artifact: String =>
            pathEndOrSingleSlash {
              get {
                onSuccess(allArtifacts(artifact)) {
                  complete(OK, _)
                }
              } ~ post {
                entity(as[String]) { request =>
                  onSuccess(createArtifact(artifact, request)) {
                    complete(Created, _)
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
                onSuccess(updateArtifact(artifact, name, request)) {
                  complete(OK, _)
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

trait RestApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def allArtifacts(artifact: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.all
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.asInstanceOf[PersistenceController[Artifact]].create(controller.unmarshall(content))
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.asInstanceOf[PersistenceController[Artifact]].update(name, controller.unmarshall(content))
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) =>
      if (content.isEmpty)
        controller.delete(name, None)
      else
        controller.asInstanceOf[PersistenceController[Artifact]].delete(name, Some(controller.unmarshall(content)))
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
    def unmarshall(content: String) = unmarshaller.read(content)

    def all(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.All(`type`)

    def create(artifact: T)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Create(artifact)

    def read(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`)

    def update(name: String, artifact: T)(implicit timeout: Timeout) = {
      if (name != artifact.name)
        error(InconsistentArtifactName(name, artifact))
      actorFor(PersistenceActor) ? PersistenceActor.Update(artifact)
    }

    def delete(name: String, artifact: Option[T])(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Delete(name, `type`)
  }

}
