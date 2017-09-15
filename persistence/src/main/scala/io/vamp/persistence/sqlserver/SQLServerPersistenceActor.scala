package io.vamp.persistence.sqlserver

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

import scala.concurrent.Future

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

  override protected def info(): Future[Map[String, Any]] = for {
    state ← super.info()
    db ← dbInfo("sqlserver")
  } yield state ++ db

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      s"insert into $table (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
    }.getOrElse(s"insert into $table (Version, Command, Type, Name) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM $table WHERE ID > $lastId ORDER BY ID ASC"

  // In Sql Server the minvalue of a select statement fetch is 0
  override val statementMinValue: Int = 0

}
