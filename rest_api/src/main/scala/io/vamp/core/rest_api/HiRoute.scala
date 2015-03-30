package io.vamp.core.rest_api

import akka.actor.Actor
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.jvm.{JmxVitalsProvider, JvmVitals}
import io.vamp.core.model.serialization.PrettyJson
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpServiceBase

import scala.language.{existentials, postfixOps}

case class HiMessage(message: String, vitals: JvmVitals) extends PrettyJson

trait HiRoute extends HttpServiceBase with JmxVitalsProvider {
  this: Actor with ExecutionContextProvider =>

  implicit def marshaller: Marshaller[Any]

  implicit def timeout: Timeout

  private lazy val hi = ConfigFactory.load().getString("vamp.message")

  val hiRoute = pathPrefix("hi") {
    pathEndOrSingleSlash {
      get {
        onSuccess(vitals()) { vitals =>
          complete(OK, HiMessage(hi, vitals))
        }
      }
    }
  }
}

