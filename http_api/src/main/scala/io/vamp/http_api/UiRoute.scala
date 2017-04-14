package io.vamp.http_api

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.{ Config, Namespace }

trait UiRoute {
  this: HttpApiDirectives ⇒

  private val index = Config.string("vamp.http-api.ui.index")
  private val directory = Config.string("vamp.http-api.ui.directory")

  def uiRoutes(implicit namespace: Namespace) = path("") {
    encodeResponse {
      if (index().isEmpty) notFound else getFromFile(index())
    }
  } ~ pathPrefix("") {
    encodeResponse {
      if (directory().isEmpty) notFound else getFromDirectory(directory())
    }
  }

  def notFound = respondWith(NotFound, HttpEntity("The requested resource could not be found."))
}
