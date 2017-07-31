package io.vamp.persistence.postgresql

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

/**
 * Maps postgres to class mapper for lifter
 */
class PostgresPersistenceActorMapper extends ClassMapper {
  val name = "postgres"
  val clazz = classOf[PostgresPersistenceActor]
}

/**
 * Support for PostgresSQL
 */
class PostgresPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override protected def info() = super.info().map(_ + ("type" → "postgres") + ("url" → url))

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      "insert into Artifacts (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
    }.getOrElse("insert into Artifacts (Version, Command, Type, Name) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM Artifacts WHERE ID > $lastId ORDER BY ID ASC"

  // In Postgres the minvalue of a select statement fetch is 0
  override val statementMinValue: Int = 0
}
