package io.magnetic.vamp_core.rest_api

import akka.actor.ActorRefFactory
import io.magnetic.vamp_common.akka.ActorRefFactoryExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.model.reader._
import io.magnetic.vamp_core.operation.DeploymentServiceProvider
import io.magnetic.vamp_core.persistance.InMemoryArtifactStoreProvider
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.routing.Route

import scala.concurrent.ExecutionContext

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
      crudRoute("filters", new NamedWeakReferenceYamlReader(FilterReader)) :+
      crudRoute("deployments", BlueprintReader, classOf[DeploymentCrudRoute])

    crudRoutes.map {
      _.route
    }.fold(documentation)((r1, r2) => r1 ~ r2)
  }

  private def crudRoute(path: String, marshaller: YamlReader[_], apiRoute: Class[_ <: ApiRoute] = classOf[CrudRoute]): CrudRoute = apiRoute match {
    case ar if ar == classOf[DeploymentCrudRoute] =>
      new DeploymentCrudRoute(path, {
        input => marshaller.asInstanceOf[ReferenceYamlReader[_]].readReferenceFromSource(input).asInstanceOf[Artifact]
      }, executionContext)

    case t =>
      new DefaultCrudRoute(path, {
        input => marshaller.read(input).asInstanceOf[Artifact]
      }, executionContext)
  }

  private def documentation: Route = new SwaggerRoute(actorRefFactory).route
}

class DefaultCrudRoute(override val path: String, override val marshaller: (String) => Artifact, override val executionContext: ExecutionContext)
  extends CrudRoute with InMemoryArtifactStoreProvider

class DeploymentCrudRoute(override val path: String, override val marshaller: (String) => Artifact, override val executionContext: ExecutionContext)
  extends CrudRoute with DeploymentServiceProvider

