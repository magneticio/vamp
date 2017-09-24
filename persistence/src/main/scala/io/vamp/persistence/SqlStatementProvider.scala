package io.vamp.persistence

/**
 * Provides the insert and delete statements for each specific sql database
 */
trait SqlStatementProvider {

  def insertStatement(): String

  def selectStatement(lastId: Long): String

  def statementMinValue: Int

}
