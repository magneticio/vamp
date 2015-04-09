package io.vamp.core.rest_api

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.common.jvm.JmxVitalsProvider
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.model.serialization.PrettyJson
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.util.Try

case class HiMessage(message: String, info: Map[String, Any]) extends PrettyJson

trait HiRoute extends HttpServiceBase with JmxVitalsProvider with FutureSupport with ActorSupport {
  this: Actor with ExecutionContextProvider =>

  implicit def marshaller: Marshaller[Any]

  implicit def timeout: Timeout

  private lazy val hiMessage = ConfigFactory.load().getString("vamp.core.hi-message")

  val hiRoute = pathPrefix("hi") {
    pathEndOrSingleSlash {
      get {
        onSuccess(info) { info =>
          complete(OK, HiMessage(hiMessage, info))
        }
      }
    }
  }

  def info: Future[Map[String, Any]] = vitals().map { vitals =>
    val containerInfo = Try(offload(actorFor(ContainerDriverActor) ? ContainerDriverActor.Info)) getOrElse Map[String, Any]()
    Map("vitals" -> vitals, "containerDriver" -> containerInfo)
  }
}

