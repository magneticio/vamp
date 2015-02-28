package io.magnetic.vamp_core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_common.akka.{ActorSupport, ExecutionContextProvider}
import io.magnetic.vamp_common.notification.NotificationErrorException
import io.magnetic.vamp_core.model.artifact.{Artifact, _}
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.rest_api.notification.{InconsistentArtifactName, RestApiNotificationProvider, UnexpectedArtifact}
import io.magnetic.vamp_core.rest_api.swagger.SwaggerResponse
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
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
    implicit val formats = Serialization.formats(NoTypeHints)
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
                  complete(OK, _)
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
              onSuccess(deleteArtifact(artifact, name)) {
                _ => complete(NoContent)
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

  private case class Mapping(`type`: Class[_], unmarshaller: YamlReader[_ <: Artifact]) {
    def all(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.All(`type`)

    def create(artifact: Artifact)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Create(artifact)

    def read(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`)

    def update(artifact: Artifact)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Update(artifact)

    def delete(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Delete(name, `type`)
  }

  private val mapping: Map[String, Mapping] = Map() +
    ("breeds" -> Mapping(classOf[Breed], BreedReader)) +
    ("blueprints" -> Mapping(classOf[Blueprint], BlueprintReader)) +
    ("slas" -> Mapping(classOf[Sla], new NamedWeakReferenceYamlReader(SlaReader))) +
    ("scales" -> Mapping(classOf[Scale], new NamedWeakReferenceYamlReader(ScaleReader))) +
    ("escalations" -> Mapping(classOf[Escalation], new NamedWeakReferenceYamlReader(EscalationReader))) +
    ("routings" -> Mapping(classOf[Routing], new NamedWeakReferenceYamlReader(RoutingReader))) +
    ("filters" -> Mapping(classOf[Filter], new NamedWeakReferenceYamlReader(FilterReader))) +
    ("deployments" -> Mapping(classOf[Blueprint], BlueprintReader))

  def allArtifacts(artifact: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(mapping: Mapping) => mapping.all
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(mapping: Mapping) => mapping.create(unmarshaller(artifact, content))
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(mapping: Mapping) => mapping.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(mapping: Mapping) =>
      val any = unmarshaller(artifact, content)
      if (name != any.asInstanceOf[Artifact].name)
        error(InconsistentArtifactName(name, content))
      mapping.update(any)
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(mapping: Mapping) => mapping.delete(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def unmarshaller(artifact: String, content: String): Artifact = mapping.get(artifact) match {
    case Some(Mapping(_, reader)) => reader.read(content)
    case None => error(UnexpectedArtifact(artifact))
  }
}
