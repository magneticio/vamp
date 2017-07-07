package io.vamp.lifter.persistence

import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.{ LifterNotificationProvider, PersistenceInitializationFailure, PersistenceInitializationSuccess }
import io.vamp.lifter.persistence.LifterPersistenceDSL.LiftAction
import io.vamp.model.resolver.NamespaceValueResolver
import LifterPersistenceDSL._
import SqlInterpreter.{ LifterResult, SqlResult }
import cats.data.Kleisli
import io.vamp.lifter.SqlLifterSeed
import cats.instances.future.catsStdInstancesForFuture
import cats.~>
import io.vamp.lifter.persistence.SqlDSL._
import scala.io.Source
import cats.implicits.catsStdInstancesForList
import cats.implicits.toTraverseOps

class SqlPersistenceInitializationActor(
    val sqlDialectInterpreter: SqlDSL ~> SqlResult,
    val sqlResource:           String
) extends CommonSupportForActors with NamespaceValueResolver with LifterNotificationProvider {

  def receive = {
    case "init" ⇒

      val createUrl = resolveWithNamespace(Config.string("vamp.lifter.sql.connection.table-url")())
      val vampDatabaseUrl = Config.string("vamp.lifter.sql.connection.database-url")()
      val db = resolveWithNamespace(Config.string("vamp.lifter.sql.database")())
      val user = Config.string("vamp.lifter.sql.user")()
      val password = Config.string("vamp.lifter.sql.password")()
      val sqlLifterSeed = SqlLifterSeed(db, user, password, createUrl, vampDatabaseUrl)

      val tableQueries: List[String] = Source
        .fromInputStream(getClass.getResourceAsStream(sqlResource))
        .mkString
        .split(';')
        .toList
        .map(_.trim)
        .filterNot(_.isEmpty)

      if (sqlResource == "sqlite.sql") {
        val createSqlLiteTableAndDatabase = for {
          connectionDB ← getConnection(default = true)
          databases ← tableQueries.traverse[SqlAction, Boolean] { tableQuery ⇒
            for {
              connection ← getConnection(default = false)
              statement ← createStatement(connection)
              result ← executeStatement(statement, tableQuery)
              _ ← closeStatement(statement)
              _ ← closeConnection(connection)
            } yield result
          }.map(_.forall(identity))
        } yield databases

        val executeSQLiteActions: Kleisli[LifterResult, SqlLifterSeed, Boolean] =
          createSqlLiteTableAndDatabase.foldMap(sqlDialectInterpreter)

        executeSQLiteActions(sqlLifterSeed).value.foreach {
          case Left(errorMessage) ⇒ reportException(PersistenceInitializationFailure(errorMessage))
          case Right(_)           ⇒ info(PersistenceInitializationSuccess)
        }

      }
      else {
        val sqlInitCommand: LiftAction[Boolean] = for {
          databaseCreated ← createDatabase
          tablesCreated ← createTables(tableQueries)
        } yield databaseCreated && tablesCreated

        val executeSqlActions: Kleisli[LifterResult, SqlLifterSeed, Boolean] =
          sqlInitCommand
            .foldMap[SqlAction](sqlInterpreter)
            .foldMap[SqlResult](sqlDialectInterpreter)

        executeSqlActions(sqlLifterSeed).value.foreach {
          case Left(errorMessage) ⇒ reportException(PersistenceInitializationFailure(errorMessage))
          case Right(_)           ⇒ info(PersistenceInitializationSuccess)
        }
      }
  }

  override def preStart(): Unit = self ! "init"

}
