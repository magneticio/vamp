package io.vamp.persistence

import io.vamp.common.ClassMapper

import scala.concurrent.Future

class PostgresPersistenceActorMapper extends ClassMapper {
  val name = "postgres"
  val clazz = classOf[PostgresPersistenceActor]
}

class PostgresPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  protected def info() = Future.successful(representationInfo() + ("type" → "postgres") + ("url" → url))

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      "insert into Artifacts (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
    }.getOrElse("insert into Artifacts (Version, Command, Type, Name) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT ID, Command, Type, Name, Definition FROM Artifacts WHERE ID > $lastId ORDER BY ID ASC"

  override val statementMinValue: Int = 0
}
