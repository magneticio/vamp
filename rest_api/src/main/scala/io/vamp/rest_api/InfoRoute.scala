package io.vamp.rest_api

import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, _ }
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.InfoController
import spray.http.StatusCodes.OK

trait InfoRoute extends InfoController with ExecutionContextProvider {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val infoRoute = pathPrefix("information" | "info") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          onSuccess(infoMessage(parameters.getOrElse("for", Nil).toSet)) { result ⇒
            respondWithStatus(OK) {
              complete(result)
            }
          }
        }
      }
    }
  }
}
