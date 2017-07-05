package io.vamp.config

import cats.data.NonEmptyList

/**
 * Config module that supplies the functions needed for reading configuration objects.
 */
object Config {

  /**
   * Reads any config value and returns Either a Non Empty List of error messages or the actual config value.
   * See ConfigReader for automatic derivation of the configuration values.
   */
  def read[A](path: String)(implicit configReader: ConfigReader[A], configSettings: ConfigSettings): Either[NonEmptyList[String], A] =
    configReader.read(path).toEither

  /**
   * Reads an optional config value.
   * Note that it looses the possible error messages for deducing to a less stronger type.
   */
  def readOpt[A](path: String)(implicit configReader: ConfigReader[Option[A]]): Option[A] =
    configReader.read(path).getOrElse(None)

}
