package io.vamp.rest_api

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.http.RestApiBase
import io.vamp.model.workflow.DefaultWorkflow
import io.vamp.persistence.db.PersistenceActor
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http.StatusCodes._

import scala.concurrent.Future

trait JavascriptWorkflowRoute {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val javascriptWorkflowRoute =
    path("workflows" / Rest) { (name: String) ⇒
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
    val artifact = DefaultWorkflow(name, None, Option(source), None)
    if (validateOnly) Future(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Update(artifact, Some(source))
  }
}
