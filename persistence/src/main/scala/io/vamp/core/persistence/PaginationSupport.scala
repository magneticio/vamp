package io.vamp.core.persistence

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorSupport, FutureSupport}
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.persistence.notification.PersistenceOperationFailure

import scala.concurrent.Future


trait PaginationSupport {
  this: NotificationProvider with ActorSupport with FutureSupport =>

  def allArtifacts(`type`: Class[_ <: Artifact])(implicit timeout: Timeout) = allPages((page: Int, perPage: Int) => {
    actorFor(PersistenceActor) ? PersistenceActor.All(`type`, page, perPage)
  })

  def allPages(perPage: (Int, Int) => Future[Any])(implicit timeout: Timeout) = {
    val maxPerPage = ArtifactResponseEnvelope.maxPerPage

    def allByPage(page: Int): (Long, List[Artifact]) = {
      offload(perPage(page, maxPerPage)) match {
        case ArtifactResponseEnvelope(list, count, _, _) => count -> list
        case any => throwException(PersistenceOperationFailure(any))
      }
    }

    val (total, artifacts) = allByPage(1)

    if (total > artifacts.size)
      (2 until (total / maxPerPage + (if (total % maxPerPage == 0) 0 else 1)).toInt).foldRight(artifacts)((i, list) => list ++ allByPage(i)._2)
    else artifacts
  }
}
