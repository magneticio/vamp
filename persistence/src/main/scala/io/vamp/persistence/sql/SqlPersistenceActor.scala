package io.vamp.persistence.sql

import io.vamp.common.{ Config, ConfigMagnet }

import scala.concurrent.duration.FiniteDuration

object SqlPersistenceActor {

  val url: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.url")
  val user: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.user")
  val password: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.password")
  val table: ConfigMagnet[String] = Config.string("vamp.persistence.database.sql.table")
  val delay: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.delay")
  val synchronizationPeriod: ConfigMagnet[FiniteDuration] = Config.duration("vamp.persistence.database.sql.synchronization.period")

}

trait SqlPersistenceActor extends CqrsActor with SqlPersistenceOperations with SqlStatementProvider {

  protected lazy val delay: FiniteDuration = SqlPersistenceActor.delay()

  protected lazy val synchronization: FiniteDuration = SqlPersistenceActor.synchronizationPeriod()

  override protected def info(): Map[String, Any] = {
    ping()
    val `type` = getClass.getSimpleName.replaceAll("PersistenceActor.*", "").toLowerCase
    super.info() + ("url" → url) + ("type" → `type`)
  }
}
