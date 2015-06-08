package io.vamp.common.http

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorDescription, CommonActorSupport}
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.vitals.{InfoRequest, JmxVitalsProvider, JvmVitals}
import spray.http.StatusCodes._
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.util.Try

trait InfoMessageBase {
  def jvm: JvmVitals
}

trait InfoBaseRoute extends HttpServiceBase with JmxVitalsProvider with CommonActorSupport {
  this: RestApiBase =>

  implicit def timeout: Timeout

  def info(jvm: JvmVitals): InfoMessageBase

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

  protected def info: Future[InfoMessageBase] = vitals().map(info)

  protected def info(actor: ActorDescription): Any = {
    Try(offload(actorFor(actor) ? InfoRequest)) getOrElse Map[String, Any]() match {
      case NotificationErrorException(_, message) => "error" -> message
      case any => any
    }
  }
}
