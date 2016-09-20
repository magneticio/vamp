package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.OK
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.InfoController

trait InfoRoute extends InfoController with ExecutionContextProvider {
  this: ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives ⇒

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
