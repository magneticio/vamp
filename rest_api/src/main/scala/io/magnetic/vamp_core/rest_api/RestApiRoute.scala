package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.model.reader.{BlueprintReader, BreedReader, YamlReader}
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.routing.{RejectionHandler, Route}

import scala.concurrent.ExecutionContext

class RestApiRoute(val ec: ExecutionContext) extends ApiRoute {

  val respondWithJson = respondWithMediaType(`application/json`)
  val noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  override def route: Route = handleRejections(RejectionHandler.Default) {
    noCachingAllowed {
      respondWithJson {
        pathPrefix("api" / "v1") {
          routes("breeds", BreedReader) ~ routes("blueprints", BlueprintReader)
        }
      }
    }
  }

  private def routes(p: String, m: YamlReader[_]) = {
    new CrudRoute with InMemoryResourceStoreProvider {
      override def path: String = p

      override def marshaller: (String) => Artifact = {
        input => m.read(input).asInstanceOf[Artifact]
      }

      override implicit def executionContext: ExecutionContext = ec
    }.route
  }
}



