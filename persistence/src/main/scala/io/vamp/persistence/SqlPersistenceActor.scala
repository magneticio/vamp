package io.vamp.persistence

import java.sql.{ DriverManager, ResultSet, Statement }

import akka.actor.Actor
import io.vamp.common.akka.SchedulerActor
import io.vamp.common.{ Artifact, ClassMapper, Config }
import io.vamp.persistence.SqlPersistenceActor.ReadAll
import io.vamp.persistence.notification.PersistenceOperationFailure
import akka.pattern.ask
import scala.concurrent.Future
import scala.util.Try

class SqlPersistenceActorMapper extends ClassMapper {
  val name = "mysql"
  val clazz = classOf[SqlPersistenceActor]
}

object SqlPersistenceActor {
  val url = Config.string("vamp.persistence.database.sql.url")
  val user = Config.string("vamp.persistence.database.sql.user")
  val password = Config.string("vamp.persistence.database.sql.password")
  val synchronizationPeriod = Config.duration("vamp.persistence.database.sql.synchronization.period")

  object ReadAll

}

class SqlPersistenceActor extends InMemoryRepresentationPersistenceActor with SchedulerActor with PersistenceMarshaller {

  protected lazy val url = SqlPersistenceActor.url()
  protected lazy val user = SqlPersistenceActor.user()
  protected lazy val password = SqlPersistenceActor.password()
  protected lazy val synchronizationPeriod = SqlPersistenceActor.synchronizationPeriod()

  private val commandSet = "SET"
  private val commandDelete = "DELETE"

  private var lastId: Long = -1

  override def receive = ({
    case ReadAll ⇒ sender ! read()
    case _: Long ⇒
  }: Actor.Receive) orElse super[SchedulerActor].receive orElse super[InMemoryRepresentationPersistenceActor].receive

  protected def info() = Future.successful(representationInfo() + ("type" → "mysql") + ("url" → url))

  override def preStart() = {
    self ! ReadAll
    self ! SchedulerActor.Period(synchronizationPeriod, synchronizationPeriod)
  }

  def tick() = read()

  protected def set(artifact: Artifact) = {
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")

    def failure: Future[Artifact] = Future.failed(new RuntimeException(s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"))

    insert(artifact.name, type2string(artifact.getClass), Option(marshall(artifact))).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(artifact), () ⇒ failure)
    } getOrElse failure
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    val kind = type2string(`type`)

    def failure: Future[Boolean] = Future.failed(new RuntimeException(s"Can not delete [${`type`.getSimpleName}] - $name}"))

    insert(name, kind).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(true), () ⇒ failure)
    } getOrElse failure
  }

  private def readOrFail[T](id: Long, succeed: () ⇒ Future[T], fail: () ⇒ Future[T]): Future[T] = {
    (self ? ReadAll).flatMap {
      _ ⇒ if (id <= lastId) succeed() else fail()
    }
  }

  private def read(): Long = {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val statement = connection.prepareStatement(
        s"SELECT `ID`, `Command`, `Type`, `Name`, `Definition` FROM `Artifacts` WHERE `ID` > $lastId ORDER BY `ID` ASC",
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY
      )
      statement.setFetchSize(Integer.MIN_VALUE)
      try {
        val result = statement.executeQuery
        while (result.next) {
          val id = result.getLong(1)
          val command = result.getString(2)
          val `type` = result.getString(3)

          if (id > lastId) {
            if (command == commandSet) {
              unmarshall(`type`, result.getString(5)).map(setArtifact)
              lastId = id
            }
            else if (command == commandDelete) {
              deleteArtifact(result.getString(4), `type`)
              lastId = id
            }
          }
        }
        lastId
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

  private def insert(name: String, kind: String, content: Option[String] = None): Try[Option[Long]] = Try {
    val connection = DriverManager.getConnection(url, user, password)
    try {
      val query = {
        if (content.isEmpty)
          "insert into Artifacts (`Command`, `Type`, `Name`) values (?, ?, ?)"
        else
          "insert into Artifacts (`Command`, `Type`, `Name`, `Definition`) values (?, ?, ?, ?)"
      }
      val statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      try {
        statement.setString(1, if (content.isDefined) commandSet else commandDelete)
        statement.setString(2, kind)
        statement.setString(3, name)
        content.foreach(statement.setString(4, _))
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
