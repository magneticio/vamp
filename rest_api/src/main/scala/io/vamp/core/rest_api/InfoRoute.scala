package io.vamp.core.rest_api

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.{InfoBaseRoute, InfoMessageBase, RestApiBase}
import io.vamp.common.vitals.JvmVitals
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.router_driver.RouterDriverActor

import scala.language.{existentials, postfixOps}

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

trait InfoRoute extends InfoBaseRoute {
  this: RestApiBase =>

  val infoMessage = ConfigFactory.load().getString("vamp.core.hi-message")

  def info(jvm: JvmVitals): InfoMessage = {
    InfoMessage(infoMessage,
      jvm,
      info(PersistenceActor),
      info(RouterDriverActor),
      info(PulseDriverActor),
      info(ContainerDriverActor)
    )
  }
}
