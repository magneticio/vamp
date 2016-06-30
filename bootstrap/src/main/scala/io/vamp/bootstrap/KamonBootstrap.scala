package io.vamp.bootstrap

import io.vamp.common.akka.Bootstrap
import kamon.Kamon

class KamonBootstrap extends Bootstrap {

  override def run() = Kamon.start()

  override def shutdown() = Kamon.shutdown()
}
