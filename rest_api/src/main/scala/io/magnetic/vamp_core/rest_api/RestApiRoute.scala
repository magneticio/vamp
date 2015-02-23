package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.model.reader._
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.routing.Route

import scala.concurrent.ExecutionContext

class RestApiRoute(val executionContext: ExecutionContext) extends ApiRoute {

  protected def jsonResponse = respondWithMediaType(`application/json`) | respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  override def route: Route = jsonResponse {
    pathPrefix("api" / "v1") {
      routes("breeds", BreedReader) ~
      routes("blueprints", BlueprintReader) ~
      routes("slas", new NamedWeakReferenceYamlReader(SlaReader)) ~
      routes("scales", new NamedWeakReferenceYamlReader(ScaleReader)) ~
      routes("escalations", new NamedWeakReferenceYamlReader(EscalationReader)) ~
      routes("routings", new NamedWeakReferenceYamlReader(RoutingReader)) ~
      routes("filters", new NamedWeakReferenceYamlReader(FilterReader))
    }
  }

  private def routes(path: String, marshaller: YamlReader[_]) = DefaultCrudRoute(path, {
    input => marshaller.read(input).asInstanceOf[Artifact]
  }, executionContext).route
}

case class DefaultCrudRoute(override val path: String, override val marshaller: (String) => Artifact, override val executionContext: ExecutionContext)
  extends CrudRoute with InMemoryResourceStoreProvider
