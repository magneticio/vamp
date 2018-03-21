package io.vamp.persistence.sql

trait SqlStatementProvider {

  def fetchSize: Int = 0

  def modifiable: Boolean = false

  def selectStatement(lastId: Long): String

  def insertStatement(): String

  def updateStatement(): String = throw new NotImplementedError

  def deleteStatement(): String = throw new NotImplementedError
}
