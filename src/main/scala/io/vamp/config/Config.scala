package io.vamp.config

import cats.data.NonEmptyList

/**
 * Config module that supplies the functions needed for reading configuration objects.
 */
object Config {

  def read[A](path: String)(implicit configReader: ConfigReader[A]): Either[NonEmptyList[String], A] =
    configReader.read(path).toEither

  def readOpt[A](path: String)(implicit configReader: ConfigReader[Option[A]]): Option[A] =
    configReader.read(path).getOrElse(None)

}
