package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.gateway_driver.haproxy.{ HaProxyConfig, HaProxyGatewayMarshaller, JTwigHaProxyGatewayMarshaller }

class GatewayDriverBootstrap extends ActorBootstrap {

  val synchronizationInitialDelay = Config.duration("vamp.operation.synchronization.initial-delay")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    HaProxyGatewayMarshaller.version() match {
      case version if version != "1.7" && version != "1.6" && version != "1.5" ⇒
        throw new RuntimeException(s"unsupported HAProxy configuration version: $version")
      case _ ⇒
    }

    val actors = List(
      IoC.createActor[GatewayDriverActor](new JTwigHaProxyGatewayMarshaller() {
        override val templateFile: String = Config.string("vamp.gateway-driver.haproxy.template")()
        override def haProxyConfig = HaProxyConfig(Config.string("vamp.gateway-driver.haproxy.ip")())
      })
    )

    actors
  }
}
