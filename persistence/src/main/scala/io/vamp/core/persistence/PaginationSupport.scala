package io.vamp.core.persistence

import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider}
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.model.event.{EventQuery, Event}
import io.vamp.core.persistence.notification.PersistenceOperationFailure
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.core.pulse.{EventRequestEnvelope, PulseActor}

import scala.concurrent.Future
import scala.util.{Failure, Success}


trait PaginationSupport {
  this: ExecutionContextProvider =>

  def allPages[T](onePage: (Int, Int) => Future[_ <: OffsetResponseEnvelope[T]])(implicit timeout: Timeout): Future[List[T]] = {
    val perPage = ArtifactResponseEnvelope.maxPerPage
    onePage(0, perPage) flatMap {
      case envelope: OffsetResponseEnvelope[T] =>
        val (total, pageList) = envelope.total -> envelope.response
        if (total > pageList.size) {
          val futures = Future(pageList) :: (1 until (total / perPage + (if (total % perPage == 0) 0 else 1)).toInt).map({ case i =>
            onePage(i * perPage, perPage).map(_.response)
          }).toList
          Future.sequence(futures.map(_.map(Success(_)).recover({ case x => Failure(x) }))).map(_.filter(_.isSuccess).flatMap(_.get))
        }
        else Future(pageList)
    }
  }
}

trait ArtifactPaginationSupport extends PaginationSupport {
  this: ActorSupport with ExecutionContextProvider with NotificationProvider =>

  def allArtifacts[T <: Artifact](`type`: Class[T])(implicit timeout: Timeout): Future[List[T]] = allPages[T]((page: Int, perPage: Int) => {
    actorFor(PersistenceActor) ? PersistenceActor.All(`type`, page, perPage) map {
      case envelope: OffsetResponseEnvelope[_] => envelope.asInstanceOf[OffsetResponseEnvelope[T]]
      case other => throwException(PersistenceOperationFailure(other))
    }
  })
}

trait EventPaginationSupport extends PaginationSupport {
  this: ActorSupport with ExecutionContextProvider with NotificationProvider =>

  def allEvents(eventQuery: EventQuery)(implicit timeout: Timeout): Future[List[Event]] = allPages[Event]((page: Int, perPage: Int) => {
    actorFor(PulseActor) ? PulseActor.Query(EventRequestEnvelope(eventQuery, page, perPage)) map {
      case envelope: OffsetResponseEnvelope[_] => envelope.asInstanceOf[OffsetResponseEnvelope[Event]]
      case other => throwException(PersistenceOperationFailure(other))
    }
  })
}
