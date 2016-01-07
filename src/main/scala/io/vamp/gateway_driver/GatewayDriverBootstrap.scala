package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.gateway_driver.aggregation.{ MetricsActor, MetricsSchedulerActor }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardInitializationActor, KibanaDashboardSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object GatewayDriverBootstrap extends Bootstrap {

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {
    val configuration = ConfigFactory.load().getConfig("vamp.gateway-driver")

    val actors = List(
      IoC.createActor[GatewayDriverActor](new HaProxyGatewayMarshaller() {
        override def tcpLogFormat: String = ConfigFactory.load().getString("vamp.gateway-driver.haproxy.tcp-log-format")
        override def httpLogFormat: String = ConfigFactory.load().getString("vamp.gateway-driver.haproxy.http-log-format")
      }),
      IoC.createActor[KibanaDashboardInitializationActor],
      IoC.createActor[KibanaDashboardActor],
      IoC.createActor[KibanaDashboardSchedulerActor],
      IoC.createActor[MetricsActor],
      IoC.createActor[MetricsSchedulerActor]
    )

    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(configuration.getInt("kibana.synchronization.period") seconds)
    IoC.actorFor[MetricsSchedulerActor] ! SchedulerActor.Period(configuration.getInt("aggregation.period") second)

    actors
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[MetricsSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(0 seconds)
    super.shutdown(actorSystem)
  }
}
