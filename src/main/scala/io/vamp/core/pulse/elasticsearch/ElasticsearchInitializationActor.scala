package io.vamp.core.pulse.elasticsearch

import akka.actor.{FSM, _}
import io.vamp.common.akka.Bootstrap.Start
import io.vamp.common.akka._
import io.vamp.common.http.RestClient
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.pulse.notification.ElasticsearchInitializationTimeoutError

import scala.language.postfixOps
import scala.util.{Failure, Success}

object ElasticsearchInitializationActor {

  sealed trait InitializationEvent

  object WaitForOne extends InitializationEvent

  object DoneWithOne extends InitializationEvent

  sealed trait State

  case object Idle extends State

  case object Active extends State

  case object Done extends State

}

trait ElasticsearchInitializationActor extends FSM[ElasticsearchInitializationActor.State, Int] with CommonSupportForActors with NotificationProvider {

  import ElasticsearchInitializationActor._

  def templates: Map[String, String]

  def timeout: akka.util.Timeout

  def elasticsearchUrl: String

  def done() = goto(Done) using 0

  startWith(Idle, 0)

  when(Idle) {
    case Event(Start, 0) =>
      log.info(s"Starting with Elasticsearch initialization.")
      initializeTemplates()
      goto(Active) using 1

    case Event(_, _) => stay()
  }

  when(Active, stateTimeout = timeout.duration) {
    case Event(WaitForOne, count) => stay() using count + 1

    case Event(DoneWithOne, count) => if (count > 1) stay() using count - 1 else done()

    case Event(StateTimeout, _) =>
      reportException(ElasticsearchInitializationTimeoutError)
      done()
  }

  when(Done) {
    case _ => stay()
  }

  initialize()

  protected def initializeTemplates() = {
    val receiver = self

    def createTemplate(name: String) = templates.get(name).foreach { template =>
      receiver ! WaitForOne
      RestClient.request[Any](s"PUT $elasticsearchUrl/_template/$name", template) onComplete {
        case _ => receiver ! DoneWithOne
      }
    }

    RestClient.request[Any](s"GET $elasticsearchUrl/_template", None, "", { case field => field }) onComplete {
      case Success(response) =>
        response match {
          case map: Map[_, _] => templates.keys.filterNot(name => map.asInstanceOf[Map[String, Any]].contains(name)).foreach(createTemplate)
          case _ => templates.keys.foreach(createTemplate)
        }
        receiver ! DoneWithOne

      case Failure(t) =>
        log.warning(s"Failed to do part of Elasticsearch initialization: $t")
        receiver ! DoneWithOne
    }
  }
}
