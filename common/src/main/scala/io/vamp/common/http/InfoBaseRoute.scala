package io.vamp.common.http

import akka.actor.Actor
import akka.pattern.{ after, ask }
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.vitals.{ InfoRequest, JmxVitalsProvider, JvmVitals }
import spray.http.StatusCodes.OK

import scala.concurrent.{ Future, TimeoutException }
import scala.language.{ existentials, postfixOps }
import scala.util.{ Failure, Success }

trait InfoMessageBase {
  def jvm: JvmVitals
}

trait InfoBaseRoute extends InfoRetrieval with JmxVitalsProvider with ExecutionContextProvider {
  this: RestApiBase with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  def componentInfoTimeout: Timeout

  val infoRoute = pathPrefix("info") {
    pathEndOrSingleSlash {
      get {
        onSuccess(info) { result ⇒
          respondWithStatus(OK) {
            complete(result)
          }
        }
      }
    }
  }

  def info: Future[InfoMessageBase]
}

trait InfoRetrieval {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  def componentInfoTimeout: Timeout

  def retrieve(actors: List[Class[Actor]]): Future[Map[Class[Actor], Any]] = {
    val futures: Map[Class[Actor], Future[Any]] = actors.map(actor ⇒ actor -> actorFor(actor) ? InfoRequest).toMap

    Future.firstCompletedOf(List(Future.sequence(futures.values.toList.map(_.recover { case x ⇒ Failure(x) })), after(componentInfoTimeout.duration, using = actorSystem.scheduler) {
      Future.successful(new TimeoutException("Component timeout."))
    })) map { _ ⇒
      futures.map {
        case (actor, future) ⇒
          actor -> (if (future.isCompleted) future.value.map {
            case Success(data) ⇒ data
            case _             ⇒ noData
          }
          else noData)
      }
    }
  }

  def noData = Map("error" -> "No response.")
}