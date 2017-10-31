package io.vamp.persistence

import akka.actor.{ Actor, Stash }
import akka.pattern.ask
import io.vamp.common.Artifact
import io.vamp.common.akka.SchedulerActor
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.CQRSActor.ReadAll

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object CQRSActor {
  sealed trait CQRSMessage
  object ReadAll extends CQRSMessage

  var SQLInitializationPerformed = false
}

/**
 * Interface for CQRS Actors
 */
trait CQRSActor extends InMemoryRepresentationPersistenceActor
    with NamespaceValueResolver
    with SchedulerActor
    with PersistenceMarshaller with Stash {

  val commandSet = "SET"
  val commandDelete = "DELETE"

  private var lastId: Long = 0

  protected def getLastId: Long = this.lastId

  protected def setLastId(newLastId: Long): Unit = this.lastId = newLastId

  protected def read(): Long

  protected def insert(name: String, kind: String, content: Option[String] = None): Try[Option[Long]]

  protected def delay: FiniteDuration

  protected def synchronization: FiniteDuration

  override def tick(): Unit = read()

  override def receive: Receive = {
    case _ ⇒
      stash; unstashAll();
      if (!CQRSActor.SQLInitializationPerformed) {
        log.info("SQL-initialization steps have not been performed; SQL-operations are not available yet.")
        context.become(waitForInitializationToBeDone)
      }
      else {
        log.info("SQL-initialization done; SQL-operations available.")
        context.become(receiveNormalBehavior)
      }
  }

  def receiveNormalBehavior: Receive = ({
    case ReadAll ⇒ sender ! read()
    case v: Long ⇒ ()
  }: Actor.Receive) orElse super[SchedulerActor].receive orElse super[InMemoryRepresentationPersistenceActor].receive

  def waitForInitializationToBeDone: Receive = {
    case "InitializationDone" ⇒ {
      log.info("SQL-initialization steps performed; SQL-operations can now be performed.")
      context.become(receiveNormalBehavior)
      unstashAll()
    }
    case _ ⇒ stash()
  }

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(delay, self, ReadAll)
    self ! SchedulerActor.Period(synchronization, synchronization)
  }

  override protected def set(artifact: Artifact): Future[Artifact] = {
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${artifact.name}")
    lazy val failMessage = s"Can not set [${artifact.getClass.getSimpleName}] - ${artifact.name}"

    insert(artifact.name, type2string(artifact.getClass), Option(marshall(artifact))).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(artifact), () ⇒ fail[Artifact](failMessage))
    }.getOrElse(fail(failMessage))
  }

  override protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    val kind = type2string(`type`)
    lazy val failMessage = s"Can not delete [${`type`.getSimpleName}] - $name}"

    insert(name, kind).collect {
      case Some(id: Long) ⇒ readOrFail(id, () ⇒ Future.successful(true), () ⇒ fail[Boolean](failMessage))
    } getOrElse fail[Boolean](failMessage)
  }

  private def fail[A](message: String): Future[A] = Future.failed(new RuntimeException(message))

  private def readOrFail[T](id: Long, succeed: () ⇒ Future[T], fail: () ⇒ Future[T]): Future[T] = {
    (self ? ReadAll).flatMap {
      _ ⇒ if (id <= lastId) succeed() else fail()
    }
  }

}
