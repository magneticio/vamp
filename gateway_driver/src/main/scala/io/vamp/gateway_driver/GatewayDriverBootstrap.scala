package io.vamp.gateway_driver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardInitializationActor, KibanaDashboardSchedulerActor }
import io.vamp.gateway_driver.zookeeper.ZooKeeperGatewayStoreActor

import scala.concurrent.duration._
import scala.language.postfixOps

object GatewayDriverBootstrap extends Bootstrap {

  override def run(implicit actorSystem: ActorSystem) = {
    IoC.alias[GatewayStore, ZooKeeperGatewayStoreActor]
    IoC.createActor[ZooKeeperGatewayStoreActor] ! Start
    IoC.createActor[GatewayDriverActor](new HaProxyGatewayMarshaller() {}) ! Start
    IoC.createActor[KibanaDashboardInitializationActor] ! Start
    IoC.createActor[KibanaDashboardActor] ! Start
    IoC.createActor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.gateway-driver.kibana.synchronization.period") seconds)
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[KibanaDashboardInitializationActor] ! Shutdown
    IoC.actorFor[KibanaDashboardActor] ! Shutdown
    IoC.actorFor[GatewayDriverActor] ! Shutdown
    IoC.actorFor[GatewayStore] ! Shutdown
  }
}
