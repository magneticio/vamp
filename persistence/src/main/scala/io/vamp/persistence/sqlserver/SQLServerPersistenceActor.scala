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

  def insertStatement(): String = s"insert into $table (Content) values (?)"

  def selectStatement(lastId: Long): String = s"SELECT ID, Content FROM $table WHERE ID > $lastId ORDER BY ID ASC"

  // In Sql Server the minvalue of a select statement fetch is 0
  val statementMinValue: Int = 0

}
