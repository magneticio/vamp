package io.vamp.common.akka

import java.util.concurrent.TimeoutException

import akka.actor.Actor
import akka.pattern.after
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait DataRetrieval {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  def retrieve(actors: List[Class[Actor]], futureOf: (Class[Actor]) ⇒ Future[Any], timeout: Timeout): Future[Map[Class[Actor], Any]] = {
    val futures: Map[Class[Actor], Future[Any]] = actors.map(actor ⇒ actor -> futureOf(actor)).toMap

    Future.firstCompletedOf(List(Future.sequence(futures.values.toList.map(_.recover { case x ⇒ Failure(x) })), after(timeout.duration, using = actorSystem.scheduler) {
      Future.successful(new TimeoutException("Component timeout."))
    })) map { _ ⇒
      futures.map {
        case (actor, future) if future.isCompleted ⇒
          actor -> future.value.map {
            case Success(data) ⇒ data
            case _             ⇒ noData(actor)
          }
        case (actor, future) ⇒ actor -> noData(actor)
      }
    }
  }

  def noData(actor: Class[Actor]) = Map("error" -> "No response.")
}
