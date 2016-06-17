package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.config.Config
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object GatewayDriverBootstrap extends Bootstrap {

  val configuration = Config
  val gatewayDriverConfiguration = configuration.config("vamp.gateway-driver")
  val haproxyConfiguration = gatewayDriverConfiguration.config("haproxy")

  val kibanaSynchronizationPeriod = gatewayDriverConfiguration.int("kibana.synchronization.period") seconds
  val synchronizationInitialDelay = configuration.int("vamp.operation.synchronization.initial-delay") seconds

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    HaProxyGatewayMarshaller.version match {
      case version if version != "1.6" && version != "1.5" ⇒ throw new RuntimeException(s"unsupported HAProxy configuration version: $version")
      case _ ⇒
    }

    val actors = List(
      IoC.createActor[GatewayDriverActor](new HaProxyGatewayMarshaller() {
        override def tcpLogFormat: String = haproxyConfiguration.string("tcp-log-format")

        override def httpLogFormat: String = haproxyConfiguration.string("http-log-format")
      }),
      IoC.createActor[KibanaDashboardActor],
      IoC.createActor[KibanaDashboardSchedulerActor]
    )

    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(kibanaSynchronizationPeriod, synchronizationInitialDelay)

    actors
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(0 seconds)
    super.shutdown(actorSystem)
  }
}
