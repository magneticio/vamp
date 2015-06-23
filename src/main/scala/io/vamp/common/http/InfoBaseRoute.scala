package io.vamp.common.http

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.InfoActor.{InfoData, InfoState}
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.vitals.{InfoRequest, JmxVitalsProvider, JvmVitals}
import spray.http.StatusCodes._

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait InfoMessageBase {
  def jvm: JvmVitals
}

trait InfoBaseRoute extends JmxVitalsProvider with ExecutionContextProvider {
  this: RestApiBase =>

  def actorRefFactory: ActorRefFactory

  implicit def timeout: Timeout

  def componentInfoTimeout: Timeout

  val infoRoute = pathPrefix("info") {
    pathEndOrSingleSlash {
      get {
        onSuccess(info) { result =>
          respondWithStatus(OK) {
            complete(result)
          }
        }
      }
    }
  }

  def info(jvm: JvmVitals): Future[InfoMessageBase]

  protected def info: Future[InfoMessageBase] = info(vitals())

  def info(actors: Set[ActorDescription]): Future[Map[ActorDescription, Any]] = {
    (actorRefFactory.actorOf(InfoActor.props()) ? InfoActor.GetInfo(actors, componentInfoTimeout)).asInstanceOf[Future[Map[ActorDescription, Any]]]
  }
}

object InfoActor {

  def props(args: Any*): Props = Props[InfoActor]

  sealed trait InfoEvent

  case class GetInfo(actors: Set[ActorDescription], timeout: Timeout) extends InfoEvent


  sealed trait InfoState

  case object InfoIdle extends InfoState

  case object InfoActive extends InfoState

  case class InfoData(receiver: ActorRef, actors: Set[ActorDescription], result: Map[ActorDescription, Any])

}

class InfoActor extends FSM[InfoState, InfoData] with ActorSupportForActors with ActorExecutionContextProvider {

  import InfoActor._

  private var timer: Option[Cancellable] = None

  startWith(InfoIdle, InfoData(ActorRef.noSender, Set(), Map()))

  when(InfoIdle) {
    case Event(GetInfo(actors, timeout), _) =>
      actors.foreach(actor => actorFor(actor) ! InfoRequest)
      timer = Some(context.system.scheduler.scheduleOnce(timeout.duration, self, StateTimeout))
      goto(InfoActive) using InfoData(sender(), actors, Map())

    case Event(_, _) => stay()
  }

  when(InfoActive) {
    case Event(StateTimeout, info) => done(info)

    case Event(response, info) => info.actors.find(actor => sender().path.toString.endsWith(s"/${actor.name}")) match {
      case None => stay()
      case Some(actor) =>
        val data = processData(actor, response, info)
        if (data.result.size == info.actors.size) done(data) else stay() using data
    }
  }

  initialize()

  private def processData(actor: ActorDescription, response: Any, info: InfoData): InfoData = {
    val result = response match {
      case NotificationErrorException(_, message) => "error" -> message
      case _ => response
    }
    info.copy(result = info.result ++ Map(actor -> result))
  }

  private def done(info: InfoData) = {
    val result = info.actors.foldLeft(info.result)((map, actor) => map.get(actor) match {
      case None => map ++ Map(actor -> ("error" -> "No response."))
      case _ => map
    })
    info.receiver ! result
    timer.map(_.cancel())
    stop()
  }
}