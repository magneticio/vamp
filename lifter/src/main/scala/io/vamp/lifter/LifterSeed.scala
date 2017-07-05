package io.vamp.lifter

import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger

case class LifterSeed(
  actorSystem:   ActorSystem,
  logger:        Logger,
  sqlLifterSeed: SqlLifterSeed)

case class SqlLifterSeed(
  db:              String,
  user:            String,
  password:        String,
  createUrl:       String,
  vampDatabaseUrl: String)
