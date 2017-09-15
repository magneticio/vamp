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

  override def getInsertStatement(content: Option[String]): String = content.map { _ ⇒
    s"INSERT INTO $table (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?);"
  }.getOrElse(s"INSERT INTO $table (Version, Command, Type, Name) values (?, ?, ?, ?);")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM $table WHERE ID > $lastId ORDER BY ID ASC;"

  override val statementMinValue: Int = 0
}
