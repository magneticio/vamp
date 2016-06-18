package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.common.config.Config
import io.vamp.gateway_driver.haproxy.{ HaProxyConfig, HaProxyGatewayMarshaller, JTwigHaProxyGatewayMarshaller }
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object GatewayDriverBootstrap extends Bootstrap {

  val haproxyConfig = Config.config("vamp.gateway-driver.haproxy")
  val kibanaSynchronizationPeriod = Config.duration("vamp.gateway-driver.kibana.synchronization.period")
  val synchronizationInitialDelay = Config.duration("vamp.operation.synchronization.initial-delay")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    HaProxyGatewayMarshaller.version match {
      case version if version != "1.6" && version != "1.5" ⇒ throw new RuntimeException(s"unsupported HAProxy configuration version: $version")
      case _ ⇒
    }

    val actors = List(
      IoC.createActor[GatewayDriverActor](new JTwigHaProxyGatewayMarshaller() {

        override val templateFile: String = Config.string("vamp.gateway-driver.haproxy.template")

        override def haProxyConfig = HaProxyConfig(
          haproxyConfig.string("ip"),
          haproxyConfig.string("virtual-hosts.ip"),
          haproxyConfig.int("virtual-hosts.port"),
          haproxyConfig.string("tcp-log-format"),
          haproxyConfig.string("http-log-format")
        )
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
