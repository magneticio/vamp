package io.vamp.bootstrap

import akka.actor.ActorSystem
import io.vamp.common.akka.Bootstrap
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.dictionary.DictionaryBootstrap
import io.vamp.gateway_driver.GatewayDriverBootstrap
import io.vamp.lifter.LifterBootstrap
import io.vamp.operation.OperationBootstrap
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap
import io.vamp.http_api.HttpApiBootstrap
import io.vamp.workflow_driver.WorkflowDriverBootstrap

class ActorBootstrap extends Bootstrap {

  implicit lazy val system = ActorSystem("vamp")

  override def run() = bootstrap.foreach(_.run)

  override def shutdown() = {
    bootstrap.reverse.foreach(_.shutdown)
    system.terminate()
  }

  private lazy val bootstrap = {
    List() :+
      PulseBootstrap :+
      PersistenceBootstrap :+
      DictionaryBootstrap :+
      ContainerDriverBootstrap :+
      GatewayDriverBootstrap :+
      WorkflowDriverBootstrap :+
      OperationBootstrap :+
      HttpApiBootstrap :+
      LifterBootstrap
  }
}
