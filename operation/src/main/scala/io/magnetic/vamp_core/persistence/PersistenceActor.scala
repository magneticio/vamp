package io.magnetic.vamp_core.persistence

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ReplyActor, RequestError}
import io.magnetic.vamp_core.model.artifact.{DefaultBreed, DefaultBlueprint}
import io.magnetic.vamp_core.persistence.notification.{PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.existentials
import scala.util.{Failure, Success}

object PersistenceActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("persistence.response.timeout").seconds)

  def props: Props = Props(new PersistenceActor)

  trait PersistenceMessages

  case class All(`type`: Class[_]) extends PersistenceMessages

  case class Create(any: AnyRef, ignoreIfExists: Boolean = false) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_]) extends PersistenceMessages

  case class Update(any: AnyRef) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_]) extends PersistenceMessages

}

class PersistenceActor extends Actor with ActorLogging with ReplyActor with InMemoryStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.persistence.PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply(request: Any) = {
    val future: Future[Any] = request match {
      case All(t) => store.all(t)
      case Create(a, ignoreIfExists) =>
        a match {
          case blueprint: DefaultBlueprint =>
            val futures = blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(store.create(_, ignoreIfExists = true)) :+ store.create(blueprint, ignoreIfExists)
            futures.reverse.foldLeft(Future(List.empty[AnyRef])) {
              (previousFuture, next) ⇒
                for {
                  previousResults <- previousFuture
                  next <- next
                } yield previousResults :+ next
            }.map(_.head)
          case any => store.create(any, ignoreIfExists)
        }
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

