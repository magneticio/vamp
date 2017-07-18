package io.vamp.persistence.sqlserver

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

/**
 * Maps sqlserver to class mapper for lifter
 */
class SQLServerPersistenceActorMapper extends ClassMapper {
  val name = "sqlserver"
  val clazz = classOf[SQLServerPersistenceActor]
}

/**
 * Support for Microsoft sql server
 */
class SQLServerPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override protected def info() = info().map(_ + ("type" → "sqlserver") + ("url" → url))

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      "insert into Artifacts (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
    }.getOrElse("insert into Artifacts (Version, Command, Type, Name) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM Artifacts WHERE ID > $lastId ORDER BY ID ASC"

  // In Sql Server the minvalue of a select statement fetch is 0
  override val statementMinValue: Int = 0

}
