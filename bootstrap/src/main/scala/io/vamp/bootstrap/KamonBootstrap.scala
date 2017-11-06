package io.vamp.bootstrap

import io.vamp.common.akka.Bootstrap
import kamon.Kamon

import scala.concurrent.Future

class KamonBootstrap extends Bootstrap {

  override def start(): Future[Unit] = Future.successful(Kamon.start())

  override def stop(): Future[Unit] = Future.successful(Kamon.shutdown())
}
