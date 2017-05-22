package io.vamp.lifter.persistence

import io.vamp.config.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.{ LifterNotificationProvider, PersistenceInitializationFailure, PersistenceInitializationSuccess }
import io.vamp.lifter.persistence.LifterPersistenceDSL.PersistenceLiftAction
import io.vamp.model.resolver.NamespaceValueResolver
import LifterPersistenceDSL._
import SqlInterpreter.{ LifterResult, SqlResult }
import cats.data.Kleisli
import io.vamp.lifter.{ LifterBootstrap, SqlLifterSeed }
import cats.instances.future.catsStdInstancesForFuture
import cats.~>
import io.vamp.lifter.persistence.SqlDSL.SqlAction

import scala.io.Source
import scala.util.{ Left, Right }

/**
 * Creates the database and tables for the underlying SQL Database
 * @param sqlDialectInterpreter Interpereter for a underlying SQL DB
 * @param sqlResource Initialization scripts for tables of the SQL Database
 */
class SqlPersistenceInitializationActor(
    val sqlDialectInterpreter: SqlDSL ~> SqlResult,
    val sqlResource:           String
) extends CommonSupportForActors with NamespaceValueResolver with LifterNotificationProvider {

  /**
   * Exists VAMP if initialization fails!
   */
  def receive = {
    case "init" ⇒
      Config.read[SqlLifterSeed]("vamp.lifter.sql") match {
        case Left(errorMessages) ⇒
          errorMessages.toList.foreach { errorMessages ⇒
            log.error(errorMessages)
          }

          log.error("Shutting down VAMP due to incomplete configuration.")
          System.exit(1)

        case Right(sls) ⇒
          // TODO better way of resolving namespace values in Strings
          val slsResolved = sls.copy(
            database = resolveWithNamespace(sls.database),
            connection = sls.connection.copy(tableUrl = resolveWithNamespace(sls.connection.tableUrl)))

          val tableQueries: List[String] = Source
            .fromInputStream(getClass.getResourceAsStream(sqlResource))
            .mkString
            .split(';')
            .toList
            .map(_.trim)
            .filterNot(_.isEmpty)

          val sqlInitActions: PersistenceLiftAction[Boolean] = for {
            databaseCreated ← createDatabase
            tablesCreated ← createTables(tableQueries)
          } yield databaseCreated && tablesCreated

          // Compiles first from PersistenceLiftAction ~> SqlDSL and then interprets to result which forms a Kleisli
          // From SqlLifterSeed (DI) to EitherT[Future, ErrorMessage, Boolean]
          val executeSqlActions: Kleisli[LifterResult, SqlLifterSeed, Boolean] =
            sqlInitActions
              .foldMap[SqlAction](sqlInterpreter)
              .foldMap[SqlResult](sqlDialectInterpreter)

          executeSqlActions.run(slsResolved).value.foreach {
            case Left(errorMessage) ⇒
              reportException(PersistenceInitializationFailure(errorMessage))
              log.error("Shutting down VAMP due to incomplete SQL initialization (ensure correct configuration values).")

              // Exit system if this critical part fails
              System.exit(LifterBootstrap.sqlInitializationExitCode)
            case Right(_) ⇒ info(PersistenceInitializationSuccess)
          }
      }
  }

  override def preStart(): Unit = self ! "init"

}
