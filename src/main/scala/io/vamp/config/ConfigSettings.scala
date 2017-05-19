package io.vamp.config

import com.typesafe.config.{ ConfigFactory, Config => TConfig }

import scala.concurrent.duration.{ MILLISECONDS, TimeUnit }

/**
 * Supplies settings and seed values needed for reading configuration values.
 *
 * See ConfigSettings.defaultSettings for default instance.
 */
trait ConfigSettings {

  /**
   * Provides the Config instance of the Typesafe config library.
   */
  def config: TConfig

  /**
   * String value used for seperating camelcase class fields to config fields.
   * For instance '-' or '_'.
   */
  def separator: String

  /**
   * TimeUnit used in retrieving FiniteDuration config values
   */
  def timeUnit: TimeUnit

}

object ConfigSettings {

  /**
   * Provides default settings for configuration.
   */
  implicit val defaultSettings: ConfigSettings = new ConfigSettings {

    override val config = ConfigFactory.load()

    override val separator = "-"

    override val timeUnit = MILLISECONDS

  }

}
