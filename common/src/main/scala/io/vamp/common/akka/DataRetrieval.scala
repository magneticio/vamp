package io.vamp.common.akka

import java.util.concurrent.TimeoutException

import akka.actor.Actor
import akka.pattern.after
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.{ Failure, Success }

case class DataRetrieved(data: Map[Class[Actor], Any], succeeded: Boolean)

trait DataRetrieval {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  def retrieve(actors: List[Class[Actor]], futureOf: (Class[Actor]) ⇒ Future[Any], timeout: Timeout): Future[DataRetrieved] = {
    def noDataError(actor: Class[Actor]) = noData(actor) → false

    val futures: Map[Class[Actor], Future[Any]] = actors.map(actor ⇒ actor → futureOf(actor)).toMap

    Future.firstCompletedOf(List(Future.sequence(futures.values.toList.map(_.recover { case x ⇒ Failure(x) })), after(timeout.duration, using = actorSystem.scheduler) {
      Future.successful(new TimeoutException("Component timeout."))
    })) map { _ ⇒
      futures.map {
        case (actor, future) if future.isCompleted ⇒
          actor → future.value.map {
            case Success(data) ⇒ data → true
            case _             ⇒ noDataError(actor)
          }.getOrElse(noDataError(actor))
        case (actor, future) ⇒ actor → noDataError(actor)
      }.foldLeft[DataRetrieved](DataRetrieved(Map(), succeeded = true)) { (r, e) ⇒ r.copy(r.data + (e._1 → e._2._1), succeeded = r.succeeded && e._2._2) }
    }
  }

  def noData(actor: Class[Actor]) = Map("error" → "No response.")
}
