package io.vamp.bootstrap

import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.Bootstrap

import scala.concurrent.Future

class ConfigurationBootstrap(implicit val namespace: Namespace) extends Bootstrap {
  override def start(): Future[Unit] = Future.successful(Config.load())
}
