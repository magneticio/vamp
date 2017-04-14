package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes.{ InternalServerError, OK }
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.InfoController

trait InfoRoute extends AbstractRoute with InfoController with ExecutionContextProvider {
  this: HttpApiDirectives ⇒

  def infoRoute(implicit namespace: Namespace, timeout: Timeout) = pathPrefix("information" | "info") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          onSuccess(infoMessage(parameters.getOrElse("on", Nil).toSet)) {
            case (result, succeeded) ⇒ respondWith(if (succeeded) OK else InternalServerError, result)
          }
        }
      }
    }
  }
}
