package io.vamp.lifter.persistence

import java.sql.DriverManager

import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.notification.PersistenceOperationFailure

import scala.io.Source

/**
 * Initializes the postgres database based on postgres.sql in resources
 */
class PostgresPersistenceInitializationActor extends CommonSupportForActors
    with NamespaceValueResolver
    with LifterNotificationProvider {

  def receive = {
    case "init" ⇒

      val url = Config.string("vamp.lifter.sql.connection.url")()
      val dbUrl = resolveWithNamespace(Config.string("vamp.lifter.sql.connection.database-url")())
      val db = resolveWithNamespace(Config.string("vamp.lifter.sql.database")())
      val user = Config.string("vamp.lifter.sql.user")()
      val password = Config.string("vamp.lifter.sql.password")()

      createDatabase(db, url, user, password)

      Source.fromInputStream(getClass.getResourceAsStream("postgres.sql")).
        mkString.split(';').map(_.trim).filterNot(_.isEmpty).foreach(execute(dbUrl, user, password, _))
  }

  override def preStart(): Unit = self ! "init"

  private def execute(url: String, user: String, password: String, query: String) = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val statement = connection.createStatement()
      try {
        statement.execute(query)
      } finally {
        statement.close()
      }
    } catch {
      case e: Exception ⇒ reportException(PersistenceOperationFailure(e))
    } finally {
      connection.close()
    }
  }

  private def createDatabase(db: String, url: String, user: String, password: String) = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      var dbExists = false

      val statement = connection.createStatement()
      statement.setFetchSize(0)

      try {
        val result = statement.executeQuery("select datname from pg_database;")

        while (result.next()) {
          if (result.getString(1) == db) dbExists = true
        }
      } finally {
        statement.close()
      }

      if (!dbExists)
        execute(url, user, password, s"""CREATE DATABASE \"$db\" ENCODING 'UTF8';""")
    } catch {
      case e: Exception ⇒ reportException(PersistenceOperationFailure(e))
    } finally {
      connection.close()
    }
  }
}
