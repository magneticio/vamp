package io.vamp.common.config

import com.typesafe.config.ConfigFactory


trait ConfigurationProvider {
  protected val confPath: String
  protected val config = ConfigFactory.load().getConfig(confPath)
}

trait DefaultConfigurationProvider extends ConfigurationProvider {
  override protected val confPath: String = ""
}