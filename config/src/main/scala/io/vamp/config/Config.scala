package io.vamp.config

import cats.data.NonEmptyList
import io.vamp.common.Namespace
import cats.syntax.either._

/**
 * Config module that supplies the functions needed for reading and retrieving configuration ADTs.
 */
object Config {

  type ConfigResult[A] = Either[NonEmptyList[String], A]

  /**
   * Adds helper functions to ConfigResult using implicit conversion.
   */
  implicit class ConfigResultOps[A](val la: ConfigResult[A]) {

    def <|>(ra: ⇒ ConfigResult[A]): ConfigResult[A] =
      la.fold(es ⇒ ra.leftMap(_.concat(es)), _.asRight)

  }

  /**
   * Reads any config value and returns Either a Non Empty List of error messages or the actual config value.
   * See ConfigReader for automatic derivation of the configuration values.
   */
  def read[A](path: String)(implicit configReader: ConfigReader[A], configSettings: ConfigSettings): ConfigResult[A] =
    configReader.read(path).toEither

  /**
   * Reads an optional config value.
   * Note that it looses the possible error messages for deducing to a less stronger type.
   */
  def readOpt[A](path: String)(implicit configReader: ConfigReader[Option[A]]): Option[A] =
    configReader.read(path).getOrElse(None)

  /**
   * Retrieves a config value from the database
   */
  def retrieve[A](kind: String)(implicit configPersistence: ConfigPersistence[A], namespace: Namespace): ConfigResult[A] =
    configPersistence.read(kind)

  /**
   * Writes a config value to the database
   */
  def write[A](kind: String, configValue: A)(implicit configPersistence: ConfigPersistence[A], namespace: Namespace): ConfigResult[Unit] =
    configPersistence.write(kind, configValue)

}
