package io.vamp.rest_api

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.http.RestApiBase
import io.vamp.model.artifact.{ DefaultBreed, Deployable }
import io.vamp.persistence.db.PersistenceActor
import io.vamp.workflow_driver.WorkflowDeployable
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http.StatusCodes._

import scala.concurrent.Future

trait JavascriptBreedRoute {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val javascriptBreedRoute =
    path("breeds" / Rest) { (name: String) ⇒
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

  private def create(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = {

    val breed = DefaultBreed(
      name = name,
      deployable = Deployable(WorkflowDeployable.`type`, source),
      ports = Nil,
      environmentVariables = Nil,
      constants = Nil,
      arguments = Nil,
      dependencies = Map()
    )

    if (validateOnly) Future(breed) else actorFor[PersistenceActor] ? PersistenceActor.Update(breed, Some(source))
  }
}
