package io.vamp.core.router_driver

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{Bootstrap, IoC}

object RouterDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor(Props(classOf[RouterDriverActor], new DefaultRouterDriver(actorSystem.dispatcher, ConfigFactory.load().getString("vamp.core.router-driver.url"))))
  }
}
