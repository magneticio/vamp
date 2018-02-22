package io.vamp.persistence.mysql

import io.vamp.common.ClassMapper
import io.vamp.persistence.{ SqlPersistenceActor, SqlStatementProvider }

class MySqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz: Class[_] = classOf[MySqlPersistenceActor]
}

class MySqlPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  def insertStatement(): String = s"insert into `$table` (`Record`) values (?)"

  def selectStatement(lastId: Long): String = s"SELECT `ID`, `Record` FROM `$table` WHERE `ID` > $lastId ORDER BY `ID` ASC"

  val statementMinValue: Int = Integer.MIN_VALUE
}
