package io.vamp.persistence.mysql

import io.vamp.common.ClassMapper
import io.vamp.persistence.sql.{ SqlPersistenceActor, SqlStatementProvider }

class MySqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz: Class[_] = classOf[MySqlPersistenceActor]
}

class MySqlPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

  override val fetchSize: Int = Integer.MIN_VALUE

  def selectStatement(lastId: Long): String = s"SELECT `ID`, `Record` FROM `$table` WHERE `ID` > $lastId ORDER BY `ID` ASC"

  def insertStatement(): String = s"insert into `$table` (`Record`) values (?)"

  override def updateStatement(id: Long, record: String): String = s"UPDATE `$table` SET `Record` = ? WHERE `ID` == $id"

  override def deleteStatement(id: Long): String = s"DELETE FROM `$table` WHERE `ID` == $id"
}
