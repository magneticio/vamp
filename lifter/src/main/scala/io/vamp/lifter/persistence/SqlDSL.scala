package io.vamp.lifter.persistence

import java.sql.{ Connection, ResultSet, Statement }
import cats.free._

/**
 * Specifies the SQL DSL for lifting persistence actions
 */
sealed trait SqlDSL[A]

case class GetConnection(default: Boolean) extends SqlDSL[Connection]

case class CloseConnection(connection: Connection) extends SqlDSL[Unit]

case class CreateStatement(connection: Connection) extends SqlDSL[Statement]

case class CloseStatement(statement: Statement) extends SqlDSL[Unit]

case class ExecuteStatement(statement: Statement, query: String) extends SqlDSL[Boolean]

case class ExecuteQuery(statement: Statement, query: String) extends SqlDSL[ResultSet]

case object CreateDatabaseQuery extends SqlDSL[String]

case object SelectDatabasesQuery extends SqlDSL[String]

case class CreateDatabaseIfNotExistsIn(resultSet: ResultSet, query: String) extends SqlDSL[Boolean]

/**
 * Contains function for lifting the SqlDSL statements in a SqlAction (Free SqlDSL -> A)
 */
object SqlDSL {

  type SqlAction[A] = Free[SqlDSL, A]

  def getConnection(default: Boolean): SqlAction[Connection] =
    Free.liftF(GetConnection(default))

  def closeConnection(connection: Connection): SqlAction[Unit] =
    Free.liftF(CloseConnection(connection))

  def createStatement(connection: Connection): SqlAction[Statement] =
    Free.liftF(CreateStatement(connection))

  def closeStatement(statement: Statement): SqlAction[Unit] =
    Free.liftF(CloseStatement(statement))

  def executeStatement(statement: Statement, query: String): SqlAction[Boolean] =
    Free.liftF(ExecuteStatement(statement, query))

  def executeQuery(statement: Statement, query: String): SqlAction[ResultSet] =
    Free.liftF(ExecuteQuery(statement, query))

  def createDatabaseQuery: SqlAction[String] =
    Free.liftF(CreateDatabaseQuery)

  def selectDatabasesQuery: SqlAction[String] =
    Free.liftF(SelectDatabasesQuery)

  def createDatabaseIfNotExistsIn(resultSet: ResultSet, query: String): SqlAction[Boolean] =
    Free.liftF(CreateDatabaseIfNotExistsIn(resultSet, query))

}