package io.vamp.bootstrap

import akka.actor._
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.dictionary.DictionaryBootstrap
import io.vamp.operation.OperationBootstrap
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap
import io.vamp.rest_api.RestApiBootstrap
import io.vamp.gateway_driver.GatewayDriverBootstrap

import scala.language.{ implicitConversions, postfixOps }

trait VampCore extends App {

  implicit val actorSystem = ActorSystem("vamp")

  def bootstrap = {
    List() :+
      PulseBootstrap :+
      PersistenceBootstrap :+
      DictionaryBootstrap :+
      ContainerDriverBootstrap :+
      GatewayDriverBootstrap :+
      OperationBootstrap :+
      RestApiBootstrap
  }

  sys.addShutdownHook {
    bootstrap.foreach(_.shutdown)
    actorSystem.terminate()
  }

  bootstrap.foreach(_.run)
}
