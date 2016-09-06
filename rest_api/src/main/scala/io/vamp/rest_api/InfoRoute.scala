package io.vamp.rest_api

import akka.http.scaladsl.model.StatusCodes.OK
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.InfoController

trait InfoRoute extends InfoController with ExecutionContextProvider {
  this: ExecutionContextProvider with ActorSystemProvider with RestApiBase ⇒

  implicit def timeout: Timeout

  val infoRoute = pathPrefix("information" | "info") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          onSuccess(infoMessage(parameters.getOrElse("on", Nil).toSet)) { result ⇒
            respondWith(OK, result)
          }
        }
      }
    }
  }
}
