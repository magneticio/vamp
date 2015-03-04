package io.magnetic.vamp_core.container_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}

object ContainerDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(ContainerDriverActor, ConfigFactory.load().getString("deployment.container.driver.url"))
  }
}
