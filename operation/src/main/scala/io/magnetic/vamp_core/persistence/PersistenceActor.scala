package io.magnetic.vamp_core.persistence

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka._
import io.magnetic.vamp_core.model.artifact.{DefaultBlueprint, DefaultBreed}
import io.magnetic.vamp_core.persistence.notification.{PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials

object PersistenceActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("persistence.response.timeout").seconds)

  def props(args: Any*): Props = Props[PersistenceActor]

  trait PersistenceMessages

  case class All(`type`: Class[_]) extends PersistenceMessages

  case class Create(any: AnyRef, ignoreIfExists: Boolean = false) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_]) extends PersistenceMessages

  case class Update(any: AnyRef, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_]) extends PersistenceMessages

}

class PersistenceActor extends Actor with ActorLogging with ReplyActor with FutureSupport with InMemoryStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.persistence.PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply(request: Any) = {
    val future: Future[Any] = request match {
      case All(ofType) => store.all(ofType)
      case Create(artifact, ignoreIfExists) =>
        artifact match {
          case blueprint: DefaultBlueprint =>
            val futures = blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map(store.create(_, ignoreIfExists = true)) :+ store.create(blueprint, ignoreIfExists)
            sequentialExecution(futures.reverse).map(_.head)
          case any => store.create(any, ignoreIfExists)
        }
      case Read(name, ofType) => store.read(name, ofType)
      case Update(artifact, create) => store.update(artifact, create)
      case Delete(name, ofType) => store.delete(name, ofType)
      case _ => error(errorRequest(request))
    }

    offLoad(future)
  }
}
