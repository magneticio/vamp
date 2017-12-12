package io.vamp.http_api

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Namespace, RootAnyMap }
import io.vamp.common.akka.IoC._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.model.artifact.{ Breed, DefaultBreed, Deployable }
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

trait JavascriptBreedRoute extends AbstractRoute with VampJsonFormats {
  this: HttpApiDirectives ⇒

  def javascriptBreedRoute(implicit namespace: Namespace, timeout: Timeout) =
    path("breeds" / Remaining) { name ⇒
      pathEndOrSingleSlash {
        (method(PUT) & contentTypeOnly(`application/javascript`)) {
          entity(as[String]) { request ⇒
            validateOnly { validateOnly ⇒
              onSuccess(create(name, request, validateOnly)) { result ⇒
                respondWith(OK, result)
              }
            }
          }
        }
      }
    }

  private def create(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    val breed = DefaultBreed(
      name = name,
      metadata = RootAnyMap.empty,
      deployable = Deployable("application/javascript", source),
      ports = Nil,
      environmentVariables = Nil,
      constants = Nil,
      arguments = Nil,
      dependencies = Map(),
      healthChecks = None
    )
    if (validateOnly) Future.successful(breed) else
      VampPersistence().update[Breed](breedSerilizationSpecifier.idExtractor(breed), _ ⇒ breed)
  }
}
