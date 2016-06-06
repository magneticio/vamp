package io.vamp.rest_api

import akka.event.Logging._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import spray.http.{ HttpEntity, HttpRequest }
import spray.http.StatusCodes._
import spray.routing.directives.LogEntry

trait UiRoute {
  this: CommonSupportForActors with RestApiBase ⇒

  private val config = ConfigFactory.load().getConfig("vamp.rest-api.ui")

  private val index = config.getString("index")
  private val directory = config.getString("directory")

  val uiRoutes = path("") {
    logRequest(showRequest _) {
      compressResponseIfRequested() {
        if (index.isEmpty) notFound else getFromFile(index)
      }
    }
  } ~ pathPrefix("") {
    logRequest(showRequest _) {
      compressResponseIfRequested() {
        if (directory.isEmpty) notFound else getFromDirectory(directory)
      }
    }
  }

  def notFound = respondWith(NotFound, HttpEntity("The requested resource could not be found."))

  def showRequest(request: HttpRequest) = LogEntry(s"${request.uri} - Headers: [${request.headers}]", InfoLevel)
}
