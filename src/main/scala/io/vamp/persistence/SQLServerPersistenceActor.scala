 package io.vamp.persistence

 import io.vamp.common.ClassMapper

 import scala.concurrent.Future

 /**
  * Maps sqlserver to class mapper for lifter
  */
 class SQLServerPersistenceActorMapper extends ClassMapper {
   val name = "sqlserver"
   val clazz = classOf[SQLServerPersistenceActor]
 }

 /**
  * Support for Microsft sql server
  */
 class SQLServerPersistenceActor extends SqlPersistenceActor with SqlStatementProvider {

   protected def info() = Future.successful(representationInfo() + ("type" → "sqlserver") + ("url" → url))

   override def getInsertStatement(content: Option[String]): String =
     content.map { _ ⇒
       "insert into Artifacts (Version, Command, Type, Name, Definition) values (?, ?, ?, ?, ?)"
     }.getOrElse("insert into Artifacts (Version, Command, Type, Name) values (?, ?, ?, ?)")

   override def getSelectStatement(lastId: Long): String =
     s"SELECT ID, Command, Type, Name, Definition FROM Artifacts WHERE ID > $lastId ORDER BY ID ASC"

   // In Postgres the minvalue of a select statement fetch is 0
   override val statementMinValue: Int = 0
 }
