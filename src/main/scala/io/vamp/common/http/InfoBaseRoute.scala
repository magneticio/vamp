package io.vamp.common.http

import akka.pattern.{after, ask}
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.vitals.{InfoRequest, JmxVitalsProvider, JvmVitals}
import spray.http.StatusCodes.OK

import scala.concurrent.{Future, TimeoutException}
import scala.language.{existentials, postfixOps}
import scala.util.{Failure, Success}

trait InfoMessageBase {
  def jvm: JvmVitals
}

trait InfoBaseRoute extends InfoRetrieval with JmxVitalsProvider with ExecutionContextProvider {
  this: RestApiBase with ActorSystemProvider =>

  implicit def timeout: Timeout

  def componentInfoTimeout: Timeout

  val infoRoute = pathPrefix("info") {
    pathEndOrSingleSlash {
      get {
        onSuccess(info) { result =>
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
  this: ExecutionContextProvider with ActorSystemProvider =>

  implicit def timeout: Timeout

  def componentInfoTimeout: Timeout

  def retrieve(actorDescriptions: List[ActorDescription]): Future[Map[ActorDescription, Any]] = {
    val futures: Map[ActorDescription, Future[Any]] = actorDescriptions.map(actorDescription => actorDescription -> IoC.actorFor(actorDescription) ? InfoRequest).toMap

    Future.firstCompletedOf(List(Future.sequence(futures.values.toList.map(_.recover { case x => Failure(x) })), after(componentInfoTimeout.duration, using = actorSystem.scheduler) {
      Future.successful(new TimeoutException("Component timeout."))
    })) map { _ =>
      futures.map { case (actorDescription, future) =>
        actorDescription -> (if (future.isCompleted) future.value.map {
          case Success(data) => data
          case _ => noData
        } else noData)
      }
    }
  }

  def noData = Map("error" -> "No response.")
}