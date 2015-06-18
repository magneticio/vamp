package io.vamp.core.rest_api

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.http.{InfoBaseRoute, InfoMessageBase, RestApiBase}
import io.vamp.common.vitals.JvmVitals
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.router_driver.RouterDriverActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any, pulseUrl : String ) extends InfoMessageBase

trait InfoRoute extends InfoBaseRoute {
  this: RestApiBase =>

  val infoMessage = ConfigFactory.load().getString("vamp.core.rest-api.info.message")

  val pulseUrl = ConfigFactory.load().getString("vamp.core.pulse-driver.url")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.core.rest-api.info.timeout") seconds)

  def info(jvm: JvmVitals): Future[InfoMessageBase] = info(Set(PersistenceActor, RouterDriverActor, PulseDriverActor, ContainerDriverActor)).map { result =>
    InfoMessage(infoMessage,
      jvm,
      result.get(PersistenceActor),
      result.get(RouterDriverActor),
      result.get(PulseDriverActor),
      result.get(ContainerDriverActor),
      pulseUrl
    )
  }
}
