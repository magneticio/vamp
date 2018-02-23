package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.CqrsActor.ReadAll
import io.vamp.persistence.notification.CorruptedDataException

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object CqrsActor {

  sealed trait CQRSMessage

  object ReadAll extends CQRSMessage

}

/**
 * Interface for CQRS Actors
 */
trait CqrsActor extends InMemoryRepresentationPersistenceActor
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
    readWrapper()
    if (synchronization.toNanos <= 0)
      ()
    else
      schedule(synchronization, delay)
  }

  //  override protected def set[T <: Artifact](artifact: T): T = {
  //    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
  //    guard()
  //    lazy val failMessage = s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"
  //
  //    insert(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact))).collect {
  //      case Some(id: Long) ⇒ readOrFail(id, () ⇒ artifact, () ⇒ fail[Artifact](failMessage))
  //    }.getOrElse(fail(failMessage)).asInstanceOf[T]
  //  }

  //  override protected def delete[T <: Artifact](name: String, `type`: Class[T]): Boolean = super.get[T](name, `type`) match {
  //    case Some(_) ⇒
  //      log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
  //      guard()
  //      val kind: String = type2string(`type`)
  //      lazy val failMessage = s"Can not delete [${`type`.getSimpleName}] - $name}"
  //
  //      insert(PersistenceRecord(name, kind)).collect {
  //        case Some(id: Long) ⇒ readOrFail(id, () ⇒ true, () ⇒ fail[Boolean](failMessage))
  //      } getOrElse fail[Boolean](failMessage)
  //
  //    case _ ⇒ false
  //  }

  //  private def fail[A](message: String): A = throw new RuntimeException(message)
  //
  //  private def readOrFail[T](id: Long, succeed: () ⇒ T, fail: () ⇒ T): T = {
  //    readWrapper()
  //    if (id <= lastId) succeed() else fail()
  //  }

  private def readWrapper(): Long = {
    try {
      read()
    }
    catch {
      case c: CorruptedDataException ⇒
        reportException(c)
        validData = false
        lastId
      case _: Exception ⇒
        // TODO: log this in a better way stacktrace does not have any additional info
        // e.printStackTrace()
        lastId
    }
    finally removeGuard()
  }
}
