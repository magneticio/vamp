package io.vamp.operation.controller.utilcontroller

import io.vamp.common.Artifact

import scala.concurrent.Future

/**
 * Controller for search actions
 */
trait SearchController {

  val elasticSearchUrlOpt: Option[String]
  val searchEnabled: Boolean

  private val notEnabledMessage: String = "VAMP Search is not enabled."

  type SearchResult[A] = Future[Either[String, List[A]]]

  def findAllArtifacts: SearchResult[Artifact] =
    if (searchEnabled) {
      elasticSearchUrlOpt.map { url ⇒
        ???
      }.getOrElse(Future.successful(Left(notEnabledMessage)))
    }
    else {
      Future.successful(Left(notEnabledMessage))
    }

  def findArtifactsBy(searchTerms: List[String]): SearchResult[Artifact] =
    if (searchEnabled) {
      elasticSearchUrlOpt.map { url ⇒
        ???
      }.getOrElse(Future.successful(Left(notEnabledMessage)))
    }
    else {
      Future.successful(Left(notEnabledMessage))
    }

}
