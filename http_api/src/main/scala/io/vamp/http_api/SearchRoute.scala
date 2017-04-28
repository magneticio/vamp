package io.vamp.http_api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkasse.EventStreamMarshalling
import io.vamp.common.{ ConfigMagnet, Namespace }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.operation.controller.SearchController

import scala.collection.immutable.Nil
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Left, Right }

/**
 * Provides the routes for searching Artifacts
 */
trait SearchRoute extends AbstractRoute with EventStreamMarshalling with SearchController {
  this: HttpApiDirectives ⇒

  def sseKeepAliveTimeout: ConfigMagnet[FiniteDuration]

  def searchRoutes(implicit namespace: Namespace) = path("search" / "artifacts") {
    parameters("query".?) { queryOpt ⇒
      val searchResults = queryOpt
        .map(query ⇒ findArtifactsBy(query.split(",").toList))
        .getOrElse(findAllArtifacts)

      onSuccess(searchResults)(handleSearchResults)
    }
  }

  private def handleSearchResults[A](searchResults: Either[String, List[A]]): Route = searchResults match {
    case Left(error) ⇒ respondWith(StatusCodes.BadRequest, error)
    case Right(sr) ⇒ sr match {
      case Nil ⇒ respondWith(StatusCodes.NotFound, "Could not find any results for this query.")
      case xs  ⇒ respondWith(StatusCodes.OK, xs)
    }
  }

}
