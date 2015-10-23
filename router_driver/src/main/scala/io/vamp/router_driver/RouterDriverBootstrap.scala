package io.vamp.router_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }

object RouterDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor[RouterDriverActor](new DefaultRouterDriver(actorSystem.dispatcher, ConfigFactory.load().getString("vamp.router-driver.url")))
  }
}
