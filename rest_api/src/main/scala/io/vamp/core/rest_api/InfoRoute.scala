package io.vamp.core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorDescription, ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.vitals.{InfoRequest, JmxVitalsProvider, JvmVitals}
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.router_driver.RouterDriverActor
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.util.Try

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any)

trait InfoRoute extends HttpServiceBase with JmxVitalsProvider with FutureSupport with ActorSupport {
  this: Actor with ExecutionContextProvider =>

  implicit def marshaller: Marshaller[Any]

  implicit def timeout: Timeout

  private lazy val infoMessage = ConfigFactory.load().getString("vamp.core.hi-message")

  val infoRoute = pathPrefix("info") {
    pathEndOrSingleSlash {
      get {
        onSuccess(info) {
          complete(OK, _)
        }
      }
    }
  }

  def info: Future[InfoMessage] = vitals().map { vitals =>
    InfoMessage(infoMessage,
      vitals,
      info(PersistenceActor),
      info(RouterDriverActor),
      info(PulseDriverActor),
      info(ContainerDriverActor)
    )
  }

  private def info(actor: ActorDescription): Any = {
    Try(offload(actorFor(actor) ? InfoRequest)) getOrElse Map[String, Any]() match {
      case NotificationErrorException(_, message) => "error" -> message
      case any => any
    }
  }
}
