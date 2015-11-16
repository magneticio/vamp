package io.vamp.pulse.elasticsearch

import akka.actor.{ FSM, _ }
import io.vamp.common.akka.Bootstrap.Start
import io.vamp.common.akka._
import io.vamp.common.http.RestClient
import io.vamp.common.notification.NotificationProvider
import io.vamp.pulse.{ ElasticsearchClient, PulseActor }
import io.vamp.pulse.notification.ElasticsearchInitializationTimeoutError

import scala.language.postfixOps
import scala.util.{ Failure, Success }

object ElasticsearchInitializationActor {

  sealed trait InitializationEvent

  object WaitForOne extends InitializationEvent

  object DoneWithOne extends InitializationEvent

  sealed trait State

  case object Idle extends State

  case object Active extends State

  case object Done extends State

  case class TemplateDefinition(name: String, template: String)

  case class DocumentDefinition(index: String, `type`: String, id: String, document: String)

}

trait ElasticsearchInitializationActor extends FSM[ElasticsearchInitializationActor.State, Int] with CommonSupportForActors with NotificationProvider {

  import ElasticsearchInitializationActor._

  def templates: List[TemplateDefinition] = Nil

  def documents: List[DocumentDefinition] = Nil

  def elasticsearchUrl: String

  lazy val timeout = PulseActor.timeout

  def done() = goto(Done) using 0

  startWith(Idle, 0)

  when(Idle) {
    case Event(Start, 0) ⇒
      log.info(s"Starting with Elasticsearch initialization.")
      goto(Active) using 3 // initializeTemplates + initializeDocuments + initializeCustom

    case Event(_, _) ⇒ stay()
  }

  onTransition {
    case Idle -> Active ⇒
      initializeTemplates()
      initializeDocuments()
      initializeCustom()
  }

  when(Active, stateTimeout = timeout.duration) {
    case Event(WaitForOne, count)  ⇒ stay() using count + 1

    case Event(DoneWithOne, count) ⇒ if (count > 1) stay() using count - 1 else done()

    case Event(StateTimeout, _) ⇒
      reportException(ElasticsearchInitializationTimeoutError)
      done()
  }

  when(Done) {
    case _ ⇒ stay()
  }

  initialize()

  protected def initializeTemplates(): Unit = {
    val receiver = self

    def createTemplate(definition: TemplateDefinition) = {
      receiver ! WaitForOne
      RestClient.put[Any](s"$elasticsearchUrl/_template/${definition.name}", definition.template) onComplete {
        case _ ⇒ receiver ! DoneWithOne
      }
    }

    RestClient.get[Any](s"$elasticsearchUrl/_template") onComplete {
      case Success(response) ⇒
        response match {
          case map: Map[_, _] ⇒ templates.filterNot(definition ⇒ map.asInstanceOf[Map[String, Any]].contains(definition.name)).foreach(createTemplate)
          case _              ⇒ templates.foreach(createTemplate)
        }
        receiver ! DoneWithOne

      case Failure(t) ⇒
        log.warning(s"Failed Elasticsearch initialization: $t")
        receiver ! DoneWithOne
    }
  }

  protected def initializeDocuments(): Unit = {
    val receiver = self
    val es = new ElasticsearchClient(elasticsearchUrl)

    documents.foreach { definition ⇒
      es.exists(definition.index, Option(definition.`type`), definition.id, () ⇒ {}, () ⇒ {
        receiver ! WaitForOne
        es.index(definition.index, definition.`type`, Option(definition.id), definition.document) onComplete {
          case _ ⇒ receiver ! DoneWithOne
        }
      })
    }

    receiver ! DoneWithOne
  }

  protected def initializeCustom(): Unit = {
    self ! DoneWithOne
  }
}
