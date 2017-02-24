package io.vamp.bootstrap

import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.Bootstrap

class ConfigurationBootstrap extends Bootstrap {
  override def start() = Config.load()(Namespace.default)
}
