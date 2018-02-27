package io.vamp.persistence.sql

trait SqlStatementProvider {

  def fetchSize: Int = 0

  def selectStatement(lastId: Long): String

  def insertStatement(): String

  def updateStatement(id: Long, record: String): String = throw new NotImplementedError

  def deleteStatement(id: Long): String = throw new NotImplementedError
}
