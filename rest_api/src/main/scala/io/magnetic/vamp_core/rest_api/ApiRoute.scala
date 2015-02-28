package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.operation.ArtifactServiceProvider
import io.magnetic.vamp_core.rest_api.notification.{RestApiNotificationProvider, UnexpectedEndOfRequest}
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing.{HttpServiceBase, Route}

trait ApiRoute extends HttpServiceBase with ExecutionContextProvider {
  def route: Route
}

trait CrudRoute extends ApiRoute with ArtifactServiceProvider with RestApiNotificationProvider {

  val `application/x-yaml` = MediaTypes.register(MediaType.custom("application/x-yaml"))

  def path: String

  def unmarshaller: String => Artifact

  private implicit val _marshaller = Marshaller.of[AnyRef](`application/json`) { (value, contentType, ctx) =>
    implicit val formats = Serialization.formats(NoTypeHints)
    ctx.marshalTo(HttpEntity(contentType, write(value)))
  }

  private implicit val _unmarshaller = Unmarshaller[Artifact](`application/json`, `application/x-yaml`) {
    case HttpEntity.NonEmpty(contentType, data) => unmarshaller(new String(data.toByteArray, contentType.charset.nioCharset))
    case HttpEntity.Empty => error(UnexpectedEndOfRequest())
  }

  final def route: Route = pathPrefix(path) {
    pathEndOrSingleSlash {
      get {
        onSuccess(artifactService.all) {
          complete(OK, _)
        }
      } ~ post {
        entity(as[Artifact]) { request =>
          onSuccess(artifactService.create(request)) {
            case Some(resource) => complete(Created, resource)
            case None => complete(BadRequest)
          }
        }
      }
    } ~ path(Segment) { name: String =>
      pathEndOrSingleSlash {
        get {
          onSuccess(artifactService.read(name)) {
            case Some(resource) => complete(OK, resource)
            case None => complete(NotFound)
          }
        } ~ put {
          entity(as[Artifact]) { request =>
            onSuccess(artifactService.update(name, request)) {
              case Some(resource) => complete(OK, resource)
              case None => complete(NotFound)
            }
          } ~ delete {
            onSuccess(artifactService.delete(name)) {
              case Some(resource) => complete(NoContent, resource)
              case None => complete(NotFound)
            }
          }
        }
      }
    }
  }
}


//if (name != artifact.name)
//error(InconsistentResourceName(name, artifact.name))