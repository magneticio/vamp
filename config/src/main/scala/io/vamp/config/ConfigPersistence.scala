package io.vamp.config

import cats.data.NonEmptyList
import io.vamp.common.Namespace
import io.vamp.config.Config.ConfigResult

/**
 * Typeclass for retrieving config values from a persistent store.
 */
trait ConfigPersistence[A] {

  /**
   * Reads an config value of type A from the persistent store.
   */
  def read(kind: String)(implicit namespace: Namespace): ConfigResult[A]

  /**
   * Writes a config value of type A to the persistent store.
   */
  def write(kind: String, configValue: A)(implicit namespace: Namespace): ConfigResult[Unit]

}

object ConfigPersistence {

  // Implicit instance for every A that has a json read and write!

  def apply[A](implicit configPersistence: ConfigPersistence[A]): ConfigPersistence[A] = configPersistence

  implicit def genericConfigPersistence[A]: ConfigPersistence[A] = ???

  // impl: SELECT Definition FROM Config WHERE kind = $kind AND namespace = $namespace
  // Try(readString('Definition').asJson.toObject) match {
  // case succ(x) => Right(x)
  // case Left(_) => Left("Unable to retrieve config value `$kind`.")

  // impl write a.toJson

}
