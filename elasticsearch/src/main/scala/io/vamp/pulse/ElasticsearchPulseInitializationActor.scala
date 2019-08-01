package io.vamp.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.NamespaceProvider
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.ElasticsearchPulseInitializationActor.TemplateDefinition

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

object ElasticsearchPulseInitializationActor {

  case class TemplateDefinition(name: String, template: String)

}

trait ElasticsearchPulseInitializationActor extends ElasticsearchPulseEvent with NamespaceValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  implicit def timeout: Timeout

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  private lazy val httpClient = new HttpClient

  private lazy val esClient = new ElasticsearchClient(ElasticsearchPulseActor.elasticsearchUrl())

  private def templates(version: Int): List[TemplateDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$version/$name.json")).mkString.replace("$NAME", indexName)

    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override lazy val indexTimeFormat: Map[String, String] = Map()

  override lazy val indexName: String = resolveWithNamespace(ElasticsearchPulseActor.indexName(), lookup = true)

  protected def initializeElasticsearch(): Future[Any] = initializeTemplates().flatMap(_ ⇒ initializeIndex(indexTypeName()._1))

  private def initializeTemplates(): Future[Any] = {
    esClient.version().flatMap {
      case Some(version) if version.take(1).toInt >= 6 ⇒ createTemplates(6)
      case _ ⇒ createTemplates(2)

    }
  }

  private def initializeIndex(indexName: String): Future[Any] = {
    httpClient.get[Any](s"${esClient.baseUrl}/$indexName", logError = false) recoverWith {
      case _ ⇒ httpClient.put[Any](s"${esClient.baseUrl}/$indexName", "")
    }
  }

  private def createTemplates(version: Int): Future[Any] = {
    def createTemplate(definition: TemplateDefinition) = httpClient.put[Any](s"${esClient.baseUrl}/_template/${definition.name}", definition.template)

    httpClient.get[Any](s"${esClient.baseUrl}/_template") map {
      case map: Map[_, _] ⇒ templates(version).filterNot(definition ⇒ map.asInstanceOf[Map[String, Any]].contains(definition.name)).map(createTemplate)
      case _              ⇒ templates(version).map(createTemplate)
    } flatMap (futures ⇒ Future.sequence(futures))
  }
}