package io.vamp.core.rest_api

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ActorSupport
import io.vamp.common.http.{InfoBaseRoute, InfoMessageBase, RestApiBase}
import io.vamp.common.vitals.JvmVitals
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.router_driver.RouterDriverActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}

case class InfoMessage(message: String, version: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

trait InfoRoute extends InfoBaseRoute {
  this: RestApiBase =>

  val infoMessage = ConfigFactory.load().getString("vamp.core.rest-api.info.message")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.core.rest-api.info.timeout") seconds)

  def info(jvm: JvmVitals): Future[InfoMessageBase] = info(Set(PersistenceActor, RouterDriverActor, PulseDriverActor, ContainerDriverActor).map(ActorSupport.alias)).map { result =>
    InfoMessage(infoMessage,
      getClass.getPackage.getImplementationVersion,
      jvm,
      result.get(ActorSupport.alias(PersistenceActor)),
      result.get(ActorSupport.alias(RouterDriverActor)),
      result.get(ActorSupport.alias(PulseDriverActor)),
      result.get(ActorSupport.alias(ContainerDriverActor))
    )
  }
}
