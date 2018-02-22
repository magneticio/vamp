package io.vamp.persistence.sqlite

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

class SQLitePersistenceActorMapper extends ClassMapper {
  override def name: String = "sqlite"

  override def clazz: Class[_] = classOf[SQLitePersistenceActor]
}

class SQLitePersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  def insertStatement(): String = s"""INSERT INTO "$table" ("Record") values (?);"""

  def selectStatement(lastId: Long): String = s"""SELECT "ID", "Record" FROM "$table" WHERE "ID" > $lastId ORDER BY "ID" ASC;"""

  override val statementMinValue: Int = 0
}
