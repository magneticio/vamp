package io.vamp.persistence.sqlite

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

import scala.concurrent.Future

class SQLitePersistenceActorMapper extends ClassMapper {
  override def name: String = "sqlite"

  override def clazz: Class[_] = classOf[SQLitePersistenceActor]
}

class SQLitePersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override def getInsertStatement(content: Option[String]): String = content.map { _ ⇒
    "INSERT INTO Artifacts (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?);"
  }.getOrElse("INSERT INTO Artifacts (Version, Command, Type, Name) values (?, ?, ?, ?);")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM Artifacts WHERE ID > $lastId ORDER BY ID ASC;"

  override val statementMinValue: Int = 0

  override protected def info(): Future[Any] =
    Future.successful(representationInfo() + ("type" → "sqlite") + ("url" → url))
}
