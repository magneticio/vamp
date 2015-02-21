package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.rest_api.util.ExecutionContextProvider
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import spray.http.ContentTypeRange._
import spray.http.HttpEntity
import spray.http.MediaTypes.`application/json`
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing.{HttpServiceBase, Route}

trait ApiRoute extends HttpServiceBase {
  def route: Route
}

trait CrudRoute extends ApiRoute with ResourceStoreProvider with ExecutionContextProvider {

  def path: String

  def marshaller: String => Artifact

  private implicit val _marshaller = Marshaller.of[AnyRef](`application/json`) { (value, contentType, ctx) =>
    implicit val formats = Serialization.formats(NoTypeHints)
    ctx.marshalTo(HttpEntity(contentType, write(value)))
  }

  private implicit val _unmarshaller = Unmarshaller[Artifact](`*`) {
    case HttpEntity.NonEmpty(contentType, data) =>
      marshaller(new String(data.toByteArray, contentType.charset.nioCharset))
    case HttpEntity.Empty => throw new RuntimeException() // TODO
  }

  final def route: Route = pathPrefix(path) {
    pathEndOrSingleSlash {
      get {
        onSuccess(resourceStore.all) {
          complete(OK, _)
        }
      } ~ post {
        entity(as[Artifact]) { request =>
          onSuccess(resourceStore.create(request)) {
            case Some(resource) => complete(Created, resource)
            case None => complete(BadRequest)
          }
        }
      }
    } ~ path(Segment) { name: String =>
      pathEndOrSingleSlash {
        get {
          onSuccess(resourceStore.find(name)) {
            complete(OK, _)
          }
        } ~ put {
          entity(as[Artifact]) { request =>
            onSuccess(resourceStore.update(name, request)) {
              case Some(resource) => complete(OK, resource)
              case None => complete(NotFound)
            }
          } ~ delete {
            onSuccess(resourceStore.delete(name)) {
              case Some(resource) => complete(NoContent, resource)
              case None => complete(NotFound)
            }
          }
        }
      }
    }
  }
}


