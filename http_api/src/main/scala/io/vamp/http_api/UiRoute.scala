package io.vamp.http_api

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import io.vamp.common.config.Config
import io.vamp.common.http.HttpApiDirectives

trait UiRoute {
  this: HttpApiDirectives ⇒

  private val index = Config.string("vamp.http-api.ui.index")()
  private val directory = Config.string("vamp.http-api.ui.directory")()

  val uiRoutes = path("") {
    encodeResponse {
      if (index.isEmpty) notFound else getFromFile(index)
    }
  } ~ pathPrefix("") {
    encodeResponse {
      if (directory.isEmpty) notFound else getFromDirectory(directory)
    }
  }

  def notFound = respondWith(NotFound, HttpEntity("The requested resource could not be found."))
}
