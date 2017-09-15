package io.vamp.persistence.postgresql

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

import scala.concurrent.Future

/**
 * Maps postgres to class mapper for lifter
 */
class PostgresPersistenceActorMapper extends ClassMapper {
  val name = "postgres"
  val clazz: Class[_] = classOf[PostgresPersistenceActor]
}

/**
 * Support for PostgresSQL
 */
class PostgresPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override protected def info(): Future[Map[String, Any]] = for {
    state ← super.info()
    db ← dbInfo("postgres")
  } yield state ++ db

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      s"insert into $table (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
    }.getOrElse(s"insert into $table (Version, Command, Type, Name) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM $table WHERE ID > $lastId ORDER BY ID ASC"

  // In Postgres the minvalue of a select statement fetch is 0
  override val statementMinValue: Int = 0
}
