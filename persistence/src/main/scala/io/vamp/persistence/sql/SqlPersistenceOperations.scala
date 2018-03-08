package io.vamp.persistence.sql

import java.sql.{ ResultSet, Statement }

import io.vamp.persistence.PersistenceRecord
import io.vamp.persistence.notification.{ CorruptedDataException, PersistenceOperationFailure }
import io.vamp.persistence.sqlconnectionpool.ConnectionPool

import scala.util.Try

trait SqlPersistenceOperations {
  this: CqrsActor with SqlStatementProvider ⇒

  protected lazy val user: String = SqlPersistenceActor.user()
  protected lazy val password: String = SqlPersistenceActor.password()
  protected lazy val url: String = resolveWithOptionalNamespace(SqlPersistenceActor.url())._1
  protected lazy val table: String = resolveWithOptionalNamespace(SqlPersistenceActor.table())._1

  protected def read(): Long = {
    log.debug(s"SQL read for table [$table] with url: $url")
    val conn = connection()
    try {
      val statement = conn.prepareStatement(
        selectStatement(lastId),
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY
      )
      statement.setFetchSize(fetchSize)
      try {
        val result = statement.executeQuery
        while (result.next) {
          val id = result.getLong(1)
          if (id > lastId) {
            dataRead(result.getString(2))
            lastId = id
          }
        }
        lastId
      }
      finally statement.close()
    }
    catch {
      case c: CorruptedDataException ⇒ throw c
      case e: Exception              ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally conn.close()
  }

  protected def insert(record: PersistenceRecord): Try[Option[Long]] = Try {
    log.debug(s"SQL insert for table [$table] with url: $url")
    val conn = connection()
    try {
      val query = insertStatement()
      val statement = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      try {
        statement.setString(1, marshallRecord(record))
        statement.executeUpdate
        val result = statement.getGeneratedKeys
        if (result.next) Option(result.getLong(1)) else None
      }
      finally statement.close()
    }
    catch {
      case e: Exception ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally conn.close()
  }

  protected def ping(): Unit = {
    log.debug(s"SQL ping for table [$table] with url: $url")
    val conn = connection()
    try {
      val statement = conn.prepareStatement("SELECT 1")
      try {
        statement.execute()
      }
      finally statement.close()
    }
    catch {
      case e: Exception ⇒ throwException(PersistenceOperationFailure(e))
    }
    finally conn.close()
  }

  private def connection() = ConnectionPool(url, user, password).getConnection
}
