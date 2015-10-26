package io.vamp.gateway_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.zookeeper.ZooKeeperStore

object GatewayDriverBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor[GatewayDriverActor](new ZooKeeperStore() {}, new HaProxyGatewayMarshaller() {})
  }
}
