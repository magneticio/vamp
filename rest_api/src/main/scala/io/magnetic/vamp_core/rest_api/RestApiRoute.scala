package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.reader.{BlueprintReader, BreedReader, YamlReader}
import io.magnetic.vamp_core.model.{Artifact, Blueprint, Breed}
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.routing.{RejectionHandler, Route}

import scala.concurrent.ExecutionContext

class RestApiRoute(val executionContext: ExecutionContext) extends ApiRoute {

  lazy val apiRoutes =
    new InMemoryCrudRoute[Breed]("breeds", BreedReader, executionContext) ::
      new InMemoryCrudRoute[Blueprint]("blueprints", BlueprintReader, executionContext) :: Nil

  val respondWithJson = respondWithMediaType(`application/json`)
  val noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  override def route: Route = handleRejections(RejectionHandler.Default) {
    noCachingAllowed {
      respondWithJson {
        pathPrefix("api" / "v1") {
          apiRoutes.map(_.route).reduce((r1, r2) => r1 ~ r2)
        }
      }
    }
  }
}

class InMemoryCrudRoute[A <: Artifact](override val path: String, override val yamlReader: YamlReader[A], override implicit val executionContext: ExecutionContext) extends CrudRoute[A] with InMemoryResourceStoreProvider[A]
