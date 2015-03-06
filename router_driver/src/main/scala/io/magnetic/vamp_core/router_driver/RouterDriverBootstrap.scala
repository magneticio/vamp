package io.magnetic.vamp_core.router_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}

object RouterDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(RouterDriverActor, ConfigFactory.load().getString("deployment.router.driver.url"))
  }
}
