package io.vamp.router_driver

import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.artifact._
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.router_driver.notification.{ RouterDriverNotificationProvider, RouterResponseError, UnsupportedRouterDriverRequest }

import scala.async.Async._
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{ Failure, Success, Try }

object RouterDriverActor {

  trait RouterDriverMessage

  object All extends RouterDriverMessage

  case class Create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean) extends RouterDriverMessage

  case class CreateEndpoint(deployment: Deployment, port: Port, update: Boolean) extends RouterDriverMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriverMessage

  case class RemoveEndpoint(deployment: Deployment, port: Port) extends RouterDriverMessage

}

/*
 * FIXME
 *
 * At the moment default Router doesn't handle well concurrent requests.
 * This is a workaround to prevent 500 response errors and this should be eventually replaced
 * by asynchronous implementation (code below).
 *
 */
class RouterDriverActor(driver: RouterDriver) extends PulseFailureNotifier with CommonSupportForActors with RouterDriverNotificationProvider {

  import io.vamp.router_driver.RouterDriverActor._

  def receive = {
    case InfoRequest                               ⇒ syncReply(driver.info)
    case All                                       ⇒ syncReply(driver.all)
    case Create(deployment, cluster, port, update) ⇒ syncReply(driver.create(deployment, cluster, port, update))
    case Remove(deployment, cluster, port)         ⇒ syncReply(driver.remove(deployment, cluster, port))
    case CreateEndpoint(deployment, port, update)  ⇒ syncReply(driver.create(deployment, port, update))
    case RemoveEndpoint(deployment, port)          ⇒ syncReply(driver.remove(deployment, port))

    case other                                     ⇒ unsupported(UnsupportedRouterDriverRequest(other))
  }

  override def errorNotificationClass = classOf[RouterResponseError]

  def syncReply[T](magnet: SyncReplyMagnet[T], `class`: Class[_ <: Notification] = errorNotificationClass): Unit = magnet.get match {
    case Success(future) ⇒
      val receiver = sender()
      async {
        receiver ! await {
          future.andThen {
            case Success(s) ⇒ s
            case Failure(f) ⇒ failure(f)
          }
        }
      }
    case Failure(f) ⇒ sender() ! failure(f)
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

sealed abstract class SyncReplyMagnet[+T] {
  def get: Try[Future[T]]
}

object SyncReplyMagnet {
  implicit def apply[T](any: ⇒ Future[T]): SyncReplyMagnet[T] = new SyncReplyMagnet[T] {
    override def get = Try(any)
  }
}

// FIXME replace above RouterDriverActor with async version (below), scala-async dependency from build.sbt and enable RouterDriverActorSpec

/*
 * Asynchronous implementation
 *
class RouterDriverActor(driver: RouterDriver) extends PulseFailureNotifier with CommonSupportForActors with RouterDriverNotificationProvider {

  import io.vamp.router_driver.RouterDriverActor._

  def receive = {
    case InfoRequest => reply(driver.info)
    case All => reply(driver.all)
    case Create(deployment, cluster, port, update) => reply(driver.create(deployment, cluster, port, update))
    case Remove(deployment, cluster, port) => reply(driver.remove(deployment, cluster, port))
    case CreateEndpoint(deployment, port, update) => reply(driver.create(deployment, port, update))
    case RemoveEndpoint(deployment, port) => reply(driver.remove(deployment, port))
    case other => unsupported(UnsupportedRouterDriverRequest(other))
  }

  override def errorNotificationClass = classOf[RouterResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}
*/ 