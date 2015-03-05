package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.operation.deployment.DeploymentActor
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.rest_api.notification.{InconsistentArtifactName, RestApiNotificationProvider, UnexpectedArtifact}
import io.magnetic.vamp_core.rest_api.serializer.{ArtifactSerializationFormat, BreedSerializationFormat, DeploymentSerializationFormat}
import io.magnetic.vamp_core.rest_api.swagger.SwaggerResponse
import org.json4s.native.Serialization._
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpEntity
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.language.existentials

trait RestApiRoute extends HttpServiceBase with RestApiController with SwaggerResponse {
  this: Actor with ExecutionContextProvider =>

  implicit def timeout: Timeout

  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  private implicit val marshaller = Marshaller.of[Any](`application/json`) { (value, contentType, ctx) =>
    implicit val formats = ArtifactSerializationFormat(BreedSerializationFormat, DeploymentSerializationFormat)

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
        } ~ path(Segment) { artifact: String =>
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

trait RestApiController extends RestApiNotificationProvider with ActorSupport {
  this: Actor with ExecutionContextProvider =>

  def allArtifacts(artifact: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(demux) => demux.all
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(demux) => demux.asInstanceOf[Demux[Artifact]].create(demux.unmarshall(content))
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(demux) => demux.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(demux) => demux.asInstanceOf[Demux[Artifact]].update(name, demux.unmarshall(content))
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(demux) =>
      if (content.isEmpty)
        demux.delete(name, None)
      else
        demux.asInstanceOf[Demux[Artifact]].delete(name, Some(demux.unmarshall(content)))
    case None => error(UnexpectedArtifact(artifact))
  }

  trait Demux[T <: Artifact] {
    def unmarshall(content: String): T

    def all(implicit timeout: Timeout): Future[Any]

    def create(artifact: T)(implicit timeout: Timeout): Future[Any]

    def read(name: String)(implicit timeout: Timeout): Future[Any]

    def update(name: String, artifact: T)(implicit timeout: Timeout): Future[Any]

    def delete(name: String, artifact: Option[T])(implicit timeout: Timeout): Future[Any]
  }

  private class PersistenceDemux[T <: Artifact](`type`: Class[_ <: Artifact], unmarshaller: YamlReader[T]) extends Demux[T] {
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

  private class DeploymentDemux() extends PersistenceDemux[Blueprint](classOf[Deployment], DeploymentBlueprintReader) with FutureSupport {
    override def unmarshall(content: String) = DeploymentBlueprintReader.readReferenceFromSource(content)

    override def create(blueprint: Blueprint)(implicit timeout: Timeout) = blueprint match {
      case reference: BlueprintReference =>
        actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint)

      case defaultBlueprint: DefaultBlueprint =>
        actorFor(PersistenceActor) ? PersistenceActor.Create(defaultBlueprint, ignoreIfExists = true)
        actorFor(DeploymentActor) ? DeploymentActor.Create(defaultBlueprint)
    }

    override def update(name: String, blueprint: Blueprint)(implicit timeout: Timeout) =
      actorFor(DeploymentActor) ? DeploymentActor.Update(name, blueprint.asInstanceOf[DefaultBlueprint])

    override def delete(name: String, blueprint: Option[Blueprint])(implicit timeout: Timeout) =
      actorFor(DeploymentActor) ? DeploymentActor.Delete(name, if (blueprint.isEmpty) None else Some(blueprint.asInstanceOf[DefaultBlueprint]))
  }

  private val mapping: Map[String, Demux[_ <: Artifact]] = Map() +
    ("breeds" -> new PersistenceDemux[Breed](classOf[Breed], BreedReader)) +
    ("blueprints" -> new PersistenceDemux[Blueprint](classOf[Blueprint], BlueprintReader)) +
    ("slas" -> new PersistenceDemux[Sla](classOf[Sla], SlaReader)) +
    ("scales" -> new PersistenceDemux[Scale](classOf[Scale], ScaleReader)) +
    ("escalations" -> new PersistenceDemux[Escalation](classOf[Escalation], EscalationReader)) +
    ("routings" -> new PersistenceDemux[Routing](classOf[Routing], RoutingReader)) +
    ("filters" -> new PersistenceDemux[Filter](classOf[Filter], FilterReader)) +
    ("deployments" -> new DeploymentDemux)
}
