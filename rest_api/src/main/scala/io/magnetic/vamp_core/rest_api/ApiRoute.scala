package io.magnetic.vamp_core.rest_api

import java.io.{ByteArrayInputStream, InputStreamReader}

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.model.reader.YamlReader
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

trait CrudRoute[A <: Artifact] extends ApiRoute with ResourceStoreProvider[A] with ExecutionContextProvider {

  def path: String

  def yamlReader: YamlReader[A]

  implicit val marshaller = Marshaller.of[AnyRef](`application/json`) { (value, contentType, ctx) =>
    implicit val formats = Serialization.formats(NoTypeHints)
    ctx.marshalTo(HttpEntity(contentType, write(value)))
  }

  implicit val unmarshaller = Unmarshaller[A](`*`) {
    case HttpEntity.NonEmpty(contentType, data) =>
      val reader = new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset)
      try {
        yamlReader.read(reader)
      }
      finally {
        reader.close()
      }
    case HttpEntity.Empty => throw new RuntimeException()
  }

  final def route: Route = pathPrefix(path) {
    pathEndOrSingleSlash {
      get {
        onSuccess(resourceStore.all) {
          complete(OK, _)
        }
      } ~ post {
        entity(as[A]) { request =>
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
          entity(as[A]) { request =>
            onSuccess(resourceStore.update(request)) {
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

