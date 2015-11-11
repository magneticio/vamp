package io.vamp.gateway_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardInitializationActor }
import io.vamp.gateway_driver.zookeeper.ZooKeeperGatewayStoreActor

object GatewayDriverBootstrap extends Bootstrap {

  override def run(implicit actorSystem: ActorSystem) = {
    IoC.alias[GatewayStore, ZooKeeperGatewayStoreActor]
    IoC.createActor[ZooKeeperGatewayStoreActor] ! Start
    IoC.createActor[GatewayDriverActor](new HaProxyGatewayMarshaller() {}) ! Start
    IoC.createActor[KibanaDashboardInitializationActor] ! Start
    IoC.createActor[KibanaDashboardActor] ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[KibanaDashboardInitializationActor] ! Shutdown
    IoC.actorFor[KibanaDashboardActor] ! Shutdown
    IoC.actorFor[GatewayDriverActor] ! Shutdown
    IoC.actorFor[GatewayStore] ! Shutdown
  }
}
