package io.vamp.persistence

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Artifact
import io.vamp.model.event.{ Event, EventQuery }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future
import scala.reflect._
import _root_.io.vamp.persistence.notification.PersistenceOperationFailure

trait PaginationSupport {
  this: ExecutionContextProvider ⇒

  def allPages[T](onePage: (Int, Int) ⇒ Future[_ <: OffsetResponseEnvelope[T]], perPage: Int = 10): Future[Stream[Future[List[T]]]] = {

    def stream(current: Int, until: Long, envelope: Future[_ <: OffsetResponseEnvelope[T]]): Stream[Future[List[T]]] = {
      if (current > until) Stream.empty else envelope.map(_.response) #:: stream(current + 1, until, onePage(current + 1, perPage))
    }

    val head = onePage(1, perPage)

    head.map {
      envelope ⇒ stream(1, (envelope.total + perPage - 1) / perPage, head)
    }
  }

  def forAll[T](all: Future[Stream[Future[List[T]]]], process: (List[T]) ⇒ Unit) = {
    all.foreach(_.foreach(_.foreach(process)))
  }

  def forEach[T](all: Future[Stream[Future[List[T]]]], process: (T) ⇒ Unit) = {
    all.foreach(_.foreach(_.foreach(_.foreach(process))))
  }

  def collectAll[A, B](all: Future[Stream[Future[List[A]]]], process: PartialFunction[List[A], B]): Future[Stream[Future[B]]] = {
    all.map(_.map(_.collect(process)))
  }

  def collectEach[A, B](all: Future[Stream[Future[List[A]]]], process: PartialFunction[A, B]): Future[Stream[Future[List[B]]]] = {
    all.map(_.map(_.map(_.collect(process))))
  }

  def consume[T](all: Future[Stream[Future[List[T]]]]): Future[List[T]] = {
    all.flatMap { stream ⇒ Future.sequence(stream.toList).map(_.flatten) }
  }
}

trait ArtifactPaginationSupport extends PaginationSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def allArtifacts[T <: Artifact: ClassTag](implicit timeout: Timeout): Future[Stream[Future[List[T]]]] = {
    allPages[T]((page: Int, perPage: Int) ⇒ {
      IoC.actorFor[PersistenceActor] ? PersistenceActor.All(classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]], page, perPage) map {
        case envelope: OffsetResponseEnvelope[_] ⇒ envelope.asInstanceOf[OffsetResponseEnvelope[T]]
        case other                               ⇒ throwException(PersistenceOperationFailure(other))
      }
    }, ArtifactResponseEnvelope.maxPerPage)
  }
}

trait EventPaginationSupport extends PaginationSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def allEvents(eventQuery: EventQuery)(implicit timeout: Timeout): Future[Stream[Future[List[Event]]]] = {
    allPages[Event]((page: Int, perPage: Int) ⇒ {
      IoC.actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, page, perPage)) map {
        case envelope: OffsetResponseEnvelope[_] ⇒ envelope.asInstanceOf[OffsetResponseEnvelope[Event]]
        case other                               ⇒ throwException(PersistenceOperationFailure(other))
      }
    }, EventRequestEnvelope.maxPerPage)
  }
}
