package io.magnetic.vamp_core.bootstrap

import akka.actor._
import io.magnetic.vamp_core.operation.OperationBootstrap
import io.magnetic.vamp_core.persistence.PersistenceBootstrap
import io.magnetic.vamp_core.rest_api.RestApiBootstrap

import scala.language.{implicitConversions, postfixOps}

object VampCore extends App {

  implicit val actorSystem = ActorSystem("vamp-core")

  PersistenceBootstrap.run
  OperationBootstrap.run
  RestApiBootstrap.run
}
