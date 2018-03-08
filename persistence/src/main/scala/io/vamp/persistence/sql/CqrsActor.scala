package io.vamp.persistence.sql

import akka.actor.Actor
import io.vamp.common.Artifact
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.AccessGuard.LoadAll
import io.vamp.persistence._
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

  protected var lastId: Long = 0

  protected def read(): Long

  protected def insert(record: PersistenceRecord): Try[Option[Long]]

  protected def delay: FiniteDuration

  protected def synchronization: FiniteDuration

  override def tick(): Unit = readAll()

  override def receive: Receive = ({
    case LoadAll ⇒ sender ! readAll()
    case _: Long ⇒
  }: Actor.Receive) orElse super[SchedulerActor].receive orElse super[PersistenceActor].receive

  override def preStart(): Unit = {
    readAll()
    if (synchronization.toNanos > 0) schedule(synchronization, delay)
  }

  override protected def set[T <: Artifact](artifact: T, kind: String): T = {
    def store(): T = {
      lazy val failMessage = s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"
      log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
      guard()
      insert(PersistenceRecord(artifact.name, artifact.kind, marshall(artifact))).collect {
        case Some(_: Long) ⇒ super.set[T](artifact, kind)
      }.getOrElse(fail(failMessage))
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
        lazy val failMessage = s"Cannot delete [$kind] - $name}"
        log.debug(s"${getClass.getSimpleName}: delete [$kind] - $name}")
        guard()
        insert(PersistenceRecord(name, kind)).collect {
          case Some(_: Long) ⇒ super.delete[T](name, kind)
        } getOrElse fail[Boolean](failMessage)
      case _ ⇒ false
    }
  }

  override protected def dataSet(artifact: Artifact, kind: String): Artifact = super.set(artifact, kind)

  override protected def dataDelete(name: String, kind: String): Unit = super.delete(name, kind)

  private def fail[A](message: String): A = throw new RuntimeException(message)

  private def readAll(): Long = {
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
