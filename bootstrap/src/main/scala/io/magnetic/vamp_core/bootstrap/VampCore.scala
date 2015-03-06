package io.magnetic.vamp_core.bootstrap

import akka.actor._
import io.magnetic.vamp_core.container_driver.ContainerDriverBootstrap
import io.magnetic.vamp_core.operation.OperationBootstrap
import io.magnetic.vamp_core.persistence.PersistenceBootstrap
import io.magnetic.vamp_core.rest_api.RestApiBootstrap
import io.magnetic.vamp_core.router_driver.RouterDriverBootstrap

import scala.language.{implicitConversions, postfixOps}

object VampCore extends App {

  implicit val actorSystem = ActorSystem("vamp-core")

  PersistenceBootstrap.run
  ContainerDriverBootstrap.run
  RouterDriverBootstrap.run
  OperationBootstrap.run
  RestApiBootstrap.run
}
