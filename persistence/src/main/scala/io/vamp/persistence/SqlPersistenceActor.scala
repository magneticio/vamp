package io.vamp.persistence

import java.sql.{ DriverManager, ResultSet, Statement }

import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.persistence.notification.{ CorruptedDataException, PersistenceOperationFailure }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object SqlPersistenceActor {

  val url: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.url")
  val user: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.user")
  val password: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.password")
  val table: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.table")
  val delay: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.delay")
  val synchronizationPeriod: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.synchronization.period")

}

trait SqlPersistenceActor extends CQRSActor with SqlStatementProvider with PersistenceDataReader {

  protected def dbInfo(`type`: String): Future[Map[String, Any]] = {
    ping()
    Future.successful(Map("type" → `type`) + ("url" → url))
  }

  protected lazy val url: String = resolveWithNamespace(SqlPersistenceActor.url())
  protected lazy val user: String = SqlPersistenceActor.user()
  protected lazy val password: String = SqlPersistenceActor.password()
  protected lazy val table: String = SqlPersistenceActor.table()

  override protected lazy val synchronization: FiniteDuration = SqlPersistenceActor.synchronizationPeriod()
  override protected lazy val delay: FiniteDuration = SqlPersistenceActor.delay()

  override protected def read(): Long = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val statement = connection.prepareStatement(
        selectStatement(getLastId),
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY
      )
      statement.setFetchSize(statementMinValue)
      try {
        val result = statement.executeQuery
        while (result.next) {
          val id = result.getLong(1)
          if (id > getLastId) {
            readData(result.getString(2))
            setLastId(id)
          }
        }
        getLastId
      }
      finally {
        statement.close()
      }
    }
    catch {
      case c: CorruptedDataException ⇒ throw c
      case e: Exception              ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally {
      connection.close()
    }
  }

  override protected def insert(record: PersistenceRecord): Try[Option[Long]] = Try {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val query = insertStatement()
      val statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      try {
        statement.setString(1, marshallRecord(record))
        statement.executeUpdate
        val result = statement.getGeneratedKeys
        if (result.next) Option(result.getLong(1)) else None
      }
      finally {
        statement.close()
      }
    }
    catch {
      case e: Exception ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally {
      connection.close()
    }
  }

  private def ping(): Unit = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val statement = connection.prepareStatement("SELECT 1")
      try {
        statement.execute()
      }
      finally {
        statement.close()
      }
    }
    catch {
      case e: Exception ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally {
      connection.close()
    }
  }
}
