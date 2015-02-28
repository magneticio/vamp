package io.magnetic.vamp_core.persistence

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ReplyActor, RequestError}
import io.magnetic.vamp_core.persistence.notification.{PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.existentials
import scala.util.{Failure, Success}

object PersistenceActor extends ActorDescription {

  def props: Props = Props(new PersistenceActor)

  trait PersistenceMessages

  case class All(`type`: Class[_]) extends PersistenceMessages

  case class Create(any: AnyRef) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_]) extends PersistenceMessages

  case class Update(any: AnyRef) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_]) extends PersistenceMessages

}

class PersistenceActor extends Actor with ActorLogging with ReplyActor with InMemoryStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.persistence.PersistenceActor._

  lazy val timeout = Timeout(ConfigFactory.load().getInt("persistence.response.timeout").seconds)

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply(request: Any) = {
    val future: Future[Any] = request match {
      case All(t) => store.all(t)
      case Create(a) => store.create(a)
      case Read(n, t) => store.read(n, t)
      case Update(a) => store.update(a)
      case Delete(n, t) => store.delete(n, t)
      case _ => error(errorRequest(request))
    }

    Await.ready(future, timeout.duration)
    future.value.get match {
      case Success(result) => result
      case Failure(result) => result
    }
  }
}

