package io.vamp.bootstrap

import io.vamp.common.akka.Bootstrap
import io.vamp.common.config.Config

class ConfigurationBootstrap extends Bootstrap {
  override def run() = Config.load()
}
