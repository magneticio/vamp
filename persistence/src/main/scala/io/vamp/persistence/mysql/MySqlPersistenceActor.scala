package io.vamp.persistence.mysql

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

import scala.concurrent.Future

class MySqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz: Class[_] = classOf[MySqlPersistenceActor]
}

class MySqlPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override protected def info(): Future[Map[String, Any]] = for {
    state ← super.info()
    db ← dbInfo("mysql")
  } yield state ++ db

  def insertStatement(): String = s"insert into $table (`Content`) values (?)"

  def selectStatement(lastId: Long): String = s"SELECT `ID`, `Content` FROM `$table` WHERE `ID` > $lastId ORDER BY `ID` ASC"

  val statementMinValue: Int = Integer.MIN_VALUE
}
