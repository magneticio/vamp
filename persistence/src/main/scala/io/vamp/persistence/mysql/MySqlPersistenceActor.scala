package io.vamp.persistence.mysql

import io.vamp.common.ClassMapper
import io.vamp.persistence.sql.SqlPersistenceActor

class MySqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz: Class[_] = classOf[MySqlPersistenceActor]
}

class MySqlPersistenceActor extends SqlPersistenceActor {

  override val fetchSize: Int = Integer.MIN_VALUE

  override val modifiable: Boolean = true

  def selectStatement(lastId: Long): String = s"SELECT `ID`, `Record` FROM `$table` WHERE `ID` > $lastId ORDER BY `ID` ASC"

  def insertStatement(): String = s"insert into `$table` (`Record`) values (?)"

  override def updateStatement(): String = s"UPDATE `$table` SET `Record` = ? WHERE `ID` = ?"

  override def deleteStatement(): String = s"DELETE FROM `$table` WHERE `ID` = ?"
}
