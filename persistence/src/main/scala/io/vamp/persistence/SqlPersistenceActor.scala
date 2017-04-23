package io.vamp.persistence

import java.sql.{ DriverManager, ResultSet, Statement }
import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.model.Model
import io.vamp.persistence.notification.PersistenceOperationFailure
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object SqlPersistenceActor {

  val url: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.url")
  val user: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.user")
  val password: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.password")
  val delay: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.delay")
  val synchronizationPeriod: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.synchronization.period")

}

trait SqlPersistenceActor extends CQRSActor with SqlStatementProvider {

  protected lazy val url: String = resolveWithNamespace(SqlPersistenceActor.url())
  protected lazy val user: String = SqlPersistenceActor.user()
  protected lazy val password: String = SqlPersistenceActor.password()
  override protected lazy val synchronization: FiniteDuration = SqlPersistenceActor.synchronizationPeriod()
  override protected lazy val delay: FiniteDuration = SqlPersistenceActor.delay()

  override protected def read(): Long = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val statement = connection.prepareStatement(
        getSelectStatement(getLastId),
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY
      )
      statement.setFetchSize(statementMinValue)
      try {
        val result = statement.executeQuery
        while (result.next) {
          val id = result.getLong(1)
          val command = result.getString(2)
          val `type` = result.getString(3)

          if (id > getLastId) {
            if (command == commandSet) {
              unmarshall(`type`, result.getString(5)).map(setArtifact)
              setLastId(id)
            }
            else if (command == commandDelete) {
              deleteArtifact(result.getString(4), `type`)
              setLastId(id)
            }
          }
        }
        getLastId
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

  override protected def insert(name: String, kind: String, content: Option[String] = None): Try[Option[Long]] = Try {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val query = getInsertStatement(content)
      val statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      try {
        statement.setString(1, Model.version)
        statement.setString(2, if (content.isDefined) commandSet else commandDelete)
        statement.setString(3, kind)
        statement.setString(4, name)
        content.foreach(statement.setString(5, _))
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
}
