package io.vamp.lifter

import scala.concurrent.duration.FiniteDuration

/**
 * Config seed for initializing sql persistence (Including nested Connection object).
 */
case class SqlLifterSeed(
  database:   String,
  user:       String,
  password:   String,
  connection: Connection)

case class Connection(tableUrl: String, databaseUrl: String)

/**
 * Config seed for initializing artifacts into VAMP.
 */
case class ArtifactLifterSeed(force: Boolean, files: List[String], resources: List[String], postpone: FiniteDuration)
