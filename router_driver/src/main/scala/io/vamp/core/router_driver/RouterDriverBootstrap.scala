package io.vamp.core.router_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap}

object RouterDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(RouterDriverActor, new DefaultRouterDriver(actorSystem.dispatcher, ConfigFactory.load().getString("deployment.router.driver.url")))
  }
}
