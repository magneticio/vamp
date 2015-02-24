package io.magnetic.vamp_core.rest_api

import akka.actor.ActorRefFactory
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.rest_api.swagger.Swagger
import io.magnetic.vamp_core.rest_api.util.ActorRefFactoryExecutionContextProvider
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.httpx.Json4sSupport
import spray.routing.Route

import scala.concurrent.ExecutionContext
import scala.io.Source

class RestApiRoute(val actorRefFactory: ActorRefFactory) extends ApiRoute with ActorRefFactoryExecutionContextProvider {

  protected def jsonResponse = respondWithMediaType(`application/json`) | respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  override def route: Route = jsonResponse {
    pathPrefix("api" / "v1") {
      endpoints
    }
  }

  private def endpoints: Route = {
    val crudRoutes = List() :+
      crudRoute("breeds", BreedReader) :+
      crudRoute("blueprints", BlueprintReader) :+
      crudRoute("slas", new NamedWeakReferenceYamlReader(SlaReader)) :+
      crudRoute("scales", new NamedWeakReferenceYamlReader(ScaleReader)) :+
      crudRoute("escalations", new NamedWeakReferenceYamlReader(EscalationReader)) :+
      crudRoute("routings", new NamedWeakReferenceYamlReader(RoutingReader)) :+
      crudRoute("filters", new NamedWeakReferenceYamlReader(FilterReader))

    crudRoutes.map {
      _.route
    }.fold(documentation)((r1, r2) => r1 ~ r2)
  }

  private def crudRoute(path: String, marshaller: YamlReader[_]): CrudRoute = new DefaultCrudRoute(path, {
    input => marshaller.read(input).asInstanceOf[Artifact]
  }, executionContext)

  private def documentation: Route = new SwaggerRoute(actorRefFactory).route
}

class DefaultCrudRoute(override val path: String, override val marshaller: (String) => Artifact, override val executionContext: ExecutionContext)
  extends CrudRoute with InMemoryResourceStoreProvider


import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.read

class SwaggerRoute(val actorRefFactory: ActorRefFactory) extends ApiRoute with Json4sSupport with ActorRefFactoryExecutionContextProvider {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit val context = actorRefFactory
  implicit val formats = Serialization.formats(NoTypeHints)

  lazy val swagger: Swagger = {
    val config = ConfigFactory.load()
    val port = config.getInt("server.port")
    val host = config.getString("server.host")

    val result = read[Swagger](Source.fromURL(getClass.getResource("/swagger/swagger.json")).bufferedReader())
    result.copy(host = s"$host:$port", basePath = "/api/v1")
  }

  def route: Route = {
    path("swagger.json") {
      complete(OK, swagger)
    } ~ path("docs") {
      complete(OK, swagger)
    } ~ path("spec") {
      complete(OK, swagger)
    }
  }
}
