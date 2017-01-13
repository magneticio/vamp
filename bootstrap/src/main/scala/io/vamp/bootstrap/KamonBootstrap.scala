package io.vamp.bootstrap

import io.vamp.common.akka.Bootstrap
import kamon.Kamon

class KamonBootstrap extends Bootstrap {

  override def start() = Kamon.start()

  override def stop() = Kamon.shutdown()
}
