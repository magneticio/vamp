package io.vamp.lifter.persistence

import java.sql.{ Connection, DriverManager, ResultSet, Statement }

import cats.data.{ EitherT, Kleisli }
import cats.~>
import io.vamp.lifter.SqlLifterSeed

import scala.concurrent.Future
import scala.util.Try

object SqlInterpreter {

  type ErrorMessage = String

  type LifterResult[A] = EitherT[Future, ErrorMessage, A]

  type SqlResult[A] = Kleisli[LifterResult, SqlLifterSeed, A]

  def tryToEitherT[A](action: ⇒ A, errorMessage: Throwable ⇒ ErrorMessage): EitherT[Future, ErrorMessage, A] =
    EitherT(Future.successful(Try(action).fold(t ⇒ Left(errorMessage(t)), Right(_))))

  def lifterResult[A](a: A): LifterResult[A] =
    EitherT[Future, ErrorMessage, A](Future.successful(Right(a)))

  def executeExecuteQuery(statement: Statement, query: String): LifterResult[ResultSet] =
    tryToEitherT(statement.executeQuery(query), _ ⇒ "Unable to execute query")

  def executeExecuteStatement(statement: Statement, query: String): LifterResult[Boolean] =
    tryToEitherT(statement.execute(query), _ ⇒ "Unable to execute statement")

  def executeCloseStatement(statement: Statement): LifterResult[Unit] =
    tryToEitherT(statement.close(), _ ⇒ "Unable to close statement.")

  def executeCreateStatement(connection: Connection): LifterResult[Statement] =
    tryToEitherT(connection.createStatement(), _ ⇒ "Unable to create statement.")

  def executeCloseConnection(connection: Connection): LifterResult[Unit] =
    tryToEitherT(connection.close(), _ ⇒ "Unable to close jdbc connection.")

  def executeGetConnection(default: Boolean, sls: SqlLifterSeed): LifterResult[Connection] = {
    val connectionUrl = if (default) sls.vampDatabaseUrl else sls.createUrl

    tryToEitherT(
      DriverManager.getConnection(connectionUrl, sls.user, sls.password),
      t ⇒ s"Unable to get connection to the database: ${t.getMessage}")
  }

  val mysqlInterpreter = new (SqlDSL ~> SqlResult) {
    override def apply[A](sqlDSL: SqlDSL[A]): SqlResult[A] =
      Kleisli[LifterResult, SqlLifterSeed, A] { sls ⇒
        sqlDSL match {
          case GetConnection(default)                        ⇒ executeGetConnection(default, sls)
          case CloseConnection(connection)                   ⇒ executeCloseConnection(connection)
          case CreateStatement(connection)                   ⇒ executeCreateStatement(connection)
          case CloseStatement(statement)                     ⇒ executeCloseStatement(statement)
          case ExecuteStatement(statement, query)            ⇒ executeExecuteStatement(statement, query)
          case ExecuteQuery(statement, query)                ⇒ executeExecuteQuery(statement, query)
          case CreateDatabaseQuery                           ⇒ lifterResult(s"CREATE DATABASE IF NOT EXISTS `${sls.db}`;")
          case SelectDatabasesQuery                          ⇒ lifterResult("show databases;")
          case CreateDatabaseIfNotExistsIn(resultSet, query) ⇒ executeCreateDatabaseIfNotExistsIn(resultSet, query, sls)
        }
      }
  }

  val sqlServerInterpreter = new (SqlDSL ~> SqlResult) {
    override def apply[A](sqlDSL: SqlDSL[A]): SqlResult[A] =
      Kleisli[LifterResult, SqlLifterSeed, A] { sls ⇒
        sqlDSL match {
          case GetConnection(default)                        ⇒ executeGetConnection(default, sls)
          case CloseConnection(connection)                   ⇒ executeCloseConnection(connection)
          case CreateStatement(connection)                   ⇒ executeCreateStatement(connection)
          case CloseStatement(statement)                     ⇒ executeCloseStatement(statement)
          case ExecuteStatement(statement, query)            ⇒ executeExecuteStatement(statement, query)
          case ExecuteQuery(statement, query)                ⇒ executeExecuteQuery(statement, query)
          case CreateDatabaseQuery                           ⇒ lifterResult(s"""CREATE DATABASE \"${sls.db}\";""") // TODO
          case SelectDatabasesQuery                          ⇒ lifterResult("select * from sys.databases;") // TODO
          case CreateDatabaseIfNotExistsIn(resultSet, query) ⇒ executeCreateDatabaseIfNotExistsIn(resultSet, query, sls)
        }
      }
  }

  val postgresqlInterpreter = new (SqlDSL ~> SqlResult) {
    override def apply[A](sqlDSL: SqlDSL[A]): SqlResult[A] =
      Kleisli[LifterResult, SqlLifterSeed, A] { sls ⇒
        sqlDSL match {
          case GetConnection(default)                        ⇒ executeGetConnection(default, sls)
          case CloseConnection(connection)                   ⇒ executeCloseConnection(connection)
          case CreateStatement(connection)                   ⇒ executeCreateStatement(connection)
          case CloseStatement(statement)                     ⇒ executeCloseStatement(statement)
          case ExecuteStatement(statement, query)            ⇒ executeExecuteStatement(statement, query)
          case ExecuteQuery(statement, query)                ⇒ executeExecuteQuery(statement, query)
          case CreateDatabaseQuery                           ⇒ lifterResult(s"""CREATE DATABASE \"${sls.db}\" ENCODING 'UTF8';""") // TODO
          case SelectDatabasesQuery                          ⇒ lifterResult("select datname from pg_database;") // TODO
          case CreateDatabaseIfNotExistsIn(resultSet, query) ⇒ executeCreateDatabaseIfNotExistsIn(resultSet, query, sls)
        }
      }
  }

  // TODO Fix this imperative while loop and state
  def executeCreateDatabaseIfNotExistsIn(resultSet: ResultSet, query: String, sls: SqlLifterSeed): LifterResult[Boolean] = {
    tryToEitherT({
      var dbExists = false

      while (resultSet.next()) {
        if (resultSet.getString(1) == sls.db) {
          dbExists = true
        }
      }

      if (!dbExists) {
        val connection = DriverManager.getConnection(sls.vampDatabaseUrl, sls.user, sls.password)
        try {
          val statement = connection.createStatement()
          try {
            statement.execute(query)
          }
          finally {
            statement.close()
          }
        }
        finally {
          connection.close()
        }
      }
      else true
    }, t ⇒ s"Unable to create database: ${t.getMessage}")
  }

}
