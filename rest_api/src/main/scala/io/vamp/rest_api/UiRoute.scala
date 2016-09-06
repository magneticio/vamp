package io.vamp.rest_api

import akka.event.Logging._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest }
import akka.http.scaladsl.server.directives.LogEntry
import io.vamp.common.config.Config
import io.vamp.common.http.RestApiBase

trait UiRoute {
  this: RestApiBase ⇒

  private val config = Config.config("vamp.rest-api.ui")

  private val index = config.string("index")
  private val directory = config.string("directory")

  val uiRoutes = path("") {
    logRequest(showRequest _) {
      encodeResponse {
        if (index.isEmpty) notFound else getFromFile(index)
      }
    }
  } ~ pathPrefix("") {
    logRequest(showRequest _) {
      encodeResponse {
        if (directory.isEmpty) notFound else getFromDirectory(directory)
      }
    }
  }

  def notFound = respondWith(NotFound, HttpEntity("The requested resource could not be found."))

  def showRequest(request: HttpRequest) = LogEntry(s"${request.uri} - Headers: [${request.headers}]", InfoLevel)
}
