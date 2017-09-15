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

  override def getInsertStatement(content: Option[String]): String =
    content.map { _ ⇒
      s"insert into $table (`Version`, `Command`, `Type`, `Name`, `Definition`) values (?, ?, ?, ?, ?)"
    }.getOrElse(s"insert into $table (`Version`, `Command`, `Type`, `Name`) values (?, ?, ?, ?)")

  override def getSelectStatement(lastId: Long): String =
    s"SELECT `ID`, `Command`, `Type`, `Name`, `Definition` FROM `$table` WHERE `ID` > $lastId ORDER BY `ID` ASC"

  override val statementMinValue: Int = Integer.MIN_VALUE
}
