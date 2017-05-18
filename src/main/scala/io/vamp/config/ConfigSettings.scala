package io.vamp.config

import com.typesafe.config.{ Config => TConfig, ConfigFactory }

/**
 * Supplies settings and seed values needed for reading configuration values
 *
 * See ConfigSettings.defaultSettings for default instance.
 */
trait ConfigSettings {

  def config: TConfig

  /**
   * String value used for seperating camelcase class fields to config fields
   * For instance '-' or '_'
   */
  def seperator: String

}

object ConfigSettings {

  implicit val defaultSettings: ConfigSettings = new ConfigSettings {

    override def config = ConfigFactory.load()

    override val seperator = "-"

  }

}
