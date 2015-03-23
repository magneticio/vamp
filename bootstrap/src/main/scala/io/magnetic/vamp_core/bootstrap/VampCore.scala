package io.magnetic.vamp_core.bootstrap

import akka.actor._
import io.magnetic.vamp_core.container_driver.ContainerDriverBootstrap
import io.magnetic.vamp_core.dictionary.DictionaryBootstrap
import io.magnetic.vamp_core.operation.OperationBootstrap
import io.magnetic.vamp_core.pulse_driver.PulseDriverBootstrap
import io.magnetic.vamp_core.rest_api.RestApiBootstrap
import io.magnetic.vamp_core.router_driver.RouterDriverBootstrap
import io.vamp.core.persistence.PersistenceBootstrap

import scala.language.{implicitConversions, postfixOps}

object VampCore extends App {

  implicit val actorSystem = ActorSystem("vamp-core")

  PersistenceBootstrap.run
  DictionaryBootstrap.run
  ContainerDriverBootstrap.run
  RouterDriverBootstrap.run
  PulseDriverBootstrap.run
  OperationBootstrap.run
  RestApiBootstrap.run
}
