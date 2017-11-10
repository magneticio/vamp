package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.Artifact
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.CQRSActor.ReadAll
import io.vamp.persistence.notification.CorruptedDataException

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object CQRSActor {

  sealed trait CQRSMessage

  object ReadAll extends CQRSMessage

}

/**
 * Interface for CQRS Actors
 */
trait CQRSActor extends InMemoryRepresentationPersistenceActor
    with AccessGuard
    with PersistenceMarshaller
    with NamespaceValueResolver
    with SchedulerActor {

  private var lastId: Long = 0

  protected def getLastId: Long = this.lastId

  protected def setLastId(newLastId: Long): Unit = this.lastId = newLastId

  protected def read(): Long

  protected def insert(record: PersistenceRecord): Try[Option[Long]]

  protected def delay: FiniteDuration

  protected def synchronization: FiniteDuration

  override def tick(): Unit = readWrapper()

  override def receive: Receive = ({
    case ReadAll ⇒ sender ! readWrapper()
    case _: Long ⇒
  }: Actor.Receive) orElse super[SchedulerActor].receive orElse super[InMemoryRepresentationPersistenceActor].receive

  override def preStart(): Unit = {
    if (synchronization.toNanos <= 0)
      context.system.scheduler.scheduleOnce(delay, self, ReadAll)
    else
      schedule(synchronization, delay)
  }

  override protected def set(artifact: Artifact): Future[Artifact] = {
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
    guard()
    lazy val failMessage = s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"

    insert(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact))).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(artifact), () ⇒ fail[Artifact](failMessage))
    }.getOrElse(fail(failMessage))
  }

  override protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    guard()
    val kind: String = type2string(`type`)
    lazy val failMessage = s"Can not delete [${`type`.getSimpleName}] - $name}"

    insert(PersistenceRecord(name, kind)).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(true), () ⇒ fail[Boolean](failMessage))
    } getOrElse fail[Boolean](failMessage)
  }

  private def fail[A](message: String): Future[A] = Future.failed(new RuntimeException(message))

  private def readOrFail[T](id: Long, succeed: () ⇒ Future[T], fail: () ⇒ Future[T]): Future[T] = {
    readWrapper
    if (id <= lastId) succeed() else fail()
  }

  private def readWrapper(): Long = {
    try {
      read()
    }
    catch {
      case c: CorruptedDataException ⇒
        reportException(c)
        validData = false
        lastId
      case e: Exception ⇒
        e.printStackTrace()
        lastId
    }
    finally removeGuard()
  }
}
