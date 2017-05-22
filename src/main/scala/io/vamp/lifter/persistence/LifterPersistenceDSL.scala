package io.vamp.lifter.persistence

import cats.free._
import cats.~>
import io.vamp.lifter.persistence.SqlDSL._
import cats.implicits.catsStdInstancesForList
import cats.implicits.toTraverseOps

/**
 * Describes the lifting action of persistence in pure data
 */
sealed trait LifterPersistenceDSL[A]

case object CreateDatabase extends LifterPersistenceDSL[Boolean]

/**
 * Creates the instruction for creating tables in each underlying SQL implementation
 * @param tableQueries are read from a sql file in persistence
 */
case class CreateTables(tableQueries: List[String]) extends LifterPersistenceDSL[Boolean]

object LifterPersistenceDSL {

  type PersistenceLiftAction[A] = Free[LifterPersistenceDSL, A]

  def createDatabase: PersistenceLiftAction[Boolean] =
    Free.liftF(CreateDatabase)

  def createTables(tableQueries: List[String]): PersistenceLiftAction[Boolean] =
    Free.liftF(CreateTables(tableQueries))

  implicit val sqlInterpreter: LifterPersistenceDSL ~> SqlAction = new (LifterPersistenceDSL ~> SqlAction) {
    def apply[A](lifterPersistenceDSL: LifterPersistenceDSL[A]): SqlAction[A] = lifterPersistenceDSL match {
      case CreateDatabase ⇒
        for {
          connection ← getConnection(default = true)
          statement ← createStatement(connection)
          query ← selectDatabasesQuery
          dbCreateQuery ← createDatabaseQuery
          result ← executeQuery(statement, query)
          dbCreated ← createDatabaseIfNotExistsIn(result, dbCreateQuery)
          _ ← closeStatement(statement)
          _ ← closeConnection(connection)
        } yield dbCreated
      case CreateTables(tableQueries) ⇒
        tableQueries.traverse[SqlAction, Boolean] { tableQuery ⇒
          for {
            connection ← getConnection(default = false)
            statement ← createStatement(connection)
            result ← executeStatement(statement, tableQuery)
            _ ← closeStatement(statement)
            _ ← closeConnection(connection)
          } yield result
        }.map(_.forall(identity))
    }
  }

}
