package io.vamp.persistence.sqlite

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

import scala.concurrent.Future

class SQLitePersistenceActorMapper extends ClassMapper {
  override def name: String = "sqlite"

  override def clazz: Class[_] = classOf[SQLitePersistenceActor]
}

class SQLitePersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override protected def info(): Future[Map[String, Any]] = for {
    state ← super.info()
    db ← dbInfo("sqlite")
  } yield state ++ db

  def insertStatement(): String = s"INSERT INTO $table (Record) values (?);"

  def selectStatement(lastId: Long): String = s"SELECT ID, Record FROM $table WHERE ID > $lastId ORDER BY ID ASC;"

  override val statementMinValue: Int = 0
}
