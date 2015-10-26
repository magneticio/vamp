package io.vamp.gateway_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.gateway_driver.haproxy.DefaultRouterDriver

object GatewayDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor[GatewayDriverActor](new DefaultRouterDriver(actorSystem.dispatcher, ConfigFactory.load().getString("vamp.router-driver.url")))
  }
}
