package io.vamp.persistence

/**
 * Provides the insert and delete statements for each specific sql database
 */
trait SqlStatementProvider {

  /**
   * Gets the statement of the specific SQL DB
   * @param content The content of the action to perform, empty when deletion
   */
  def getInsertStatement(content: Option[String]): String

  def getSelectStatement(lastId: Long): String

  def statementMinValue: Int

}
