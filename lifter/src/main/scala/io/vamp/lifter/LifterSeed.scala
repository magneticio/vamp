package io.vamp.lifter

case class SqlLifterSeed(
  db:              String,
  user:            String,
  password:        String,
  createUrl:       String,
  vampDatabaseUrl: String)
