package io.vamp.persistence

import akka.actor.Actor
import io.vamp.common.Artifact
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.AccessGuard.LoadAll
import io.vamp.persistence.notification.CorruptedDataException

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait CqrsActor
    extends PersistenceActor
    with PersistenceRepresentation
    with PersistenceMarshaller
    with PersistenceDataReader
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
    case LoadAll ⇒ sender ! readWrapper()
    case _: Long ⇒
  }: Actor.Receive) orElse super[SchedulerActor].receive orElse super[PersistenceActor].receive

  override def preStart(): Unit = {
    readWrapper()
    if (synchronization.toNanos > 0) schedule(synchronization, delay)
  }

  override protected def set[T <: Artifact](artifact: T, kind: String): T = {
    def store(): T = {
      lazy val failMessage = s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"
      log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
      guard()
      insert(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact))).collect {
        case Some(id: Long) ⇒ readOrFail(id, () ⇒ artifact, () ⇒ fail[Artifact](failMessage))
      }.getOrElse(fail(failMessage)).asInstanceOf[T]
    }

    super.get[T](artifact.name, kind) match {
      case Some(a) if a != artifact ⇒ store()
      case Some(_)                  ⇒ artifact
      case None                     ⇒ store()
    }
  }

  override protected def delete[T <: Artifact](name: String, kind: String): Boolean = {
    super.get[T](name, kind) match {
      case Some(_) ⇒
        lazy val failMessage = s"Can not delete [$kind] - $name}"
        log.debug(s"${getClass.getSimpleName}: delete [$kind] - $name}")
        guard()
        insert(PersistenceRecord(name, kind)).collect {
          case Some(id: Long) ⇒ readOrFail(id, () ⇒ true, () ⇒ fail[Boolean](failMessage))
        } getOrElse fail[Boolean](failMessage)
      case _ ⇒ false
    }
  }

  override protected def dataSet(artifact: Artifact, kind: String): Artifact = super.set(artifact, kind)

  override protected def dataDelete(name: String, kind: String): Unit = super.delete(name, kind)

  private def fail[A](message: String): A = throw new RuntimeException(message)

  private def readOrFail[T](id: Long, succeed: () ⇒ T, fail: () ⇒ T): T = {
    readWrapper()
    if (id <= lastId) succeed() else fail()
  }

  private def readWrapper(): Long = {
    try read() catch {
      case c: CorruptedDataException ⇒
        reportException(c)
        validData = false
        lastId
      case e: Exception ⇒
        log.warning(e.getMessage)
        lastId
    }
    finally removeGuard()
  }
}
