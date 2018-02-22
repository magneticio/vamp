package io.vamp.persistence.sqlserver

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

/**
 * Maps sqlserver to class mapper for lifter
 */
class SQLServerPersistenceActorMapper extends ClassMapper {
  val name = "sqlserver"
  val clazz: Class[_] = classOf[SQLServerPersistenceActor]
}

/**
 * Support for Microsoft sql server
 */
class SQLServerPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  def insertStatement(): String = s"insert into [$table] ([Record]) values (?)"

  def selectStatement(lastId: Long): String = s"SELECT [ID], [Record] FROM [$table] WHERE [ID] > $lastId ORDER BY [ID] ASC"

  // In Sql Server the minvalue of a select statement fetch is 0
  val statementMinValue: Int = 0
}
