package io.vamp.rest_api

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.RestApiBase
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.{ DefaultBreed, Deployable }
import io.vamp.persistence.db.PersistenceActor
import io.vamp.workflow_driver.WorkflowDeployable

import scala.concurrent.Future

trait JavascriptBreedRoute {
  this: ExecutionContextProvider with ActorSystemProvider with RestApiBase with NotificationProvider ⇒

  implicit def timeout: Timeout

  val javascriptBreedRoute =
    path("breeds") {
      extractUnmatchedPath { remaining ⇒
        pathEndOrSingleSlash {
          (method(PUT) & contentTypeOnly(`application/javascript`)) {
            entity(as[String]) { request ⇒
              validateOnly { validateOnly ⇒
                onSuccess(create(remaining.toString, request, validateOnly)) { result ⇒
                  respondWith(OK, result)
                }
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

    if (validateOnly) Future.successful(breed) else actorFor[PersistenceActor] ? PersistenceActor.Update(breed, Some(source))
  }
}
