package io.vamp.pulse.elasticsearch

import io.vamp.common.http.RestClient
import io.vamp.pulse.PulseActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.{ DoneWithOne, TemplateDefinition, WaitForOne }
import io.vamp.pulse.notification.PulseNotificationProvider

import scala.io.Source
import scala.util.Success

class PulseInitializationActor extends ElasticsearchInitializationActor with PulseNotificationProvider {

  import PulseActor._

  lazy val elasticsearchUrl = PulseActor.elasticsearchUrl

  override lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", indexName)
    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override protected def initializeCustom(): Unit = {
    val receiver = self

    RestClient.get[Any](s"$elasticsearchUrl/$indexName", RestClient.jsonHeaders, logError = false) onComplete {
      case Success(map: Map[_, _]) if map.asInstanceOf[Map[String, Any]].getOrElse("found", false) == true ⇒
      case _ ⇒
        receiver ! WaitForOne
        RestClient.put[Any](s"$elasticsearchUrl/$indexName", "") onComplete {
          case _ ⇒ receiver ! DoneWithOne
        }
    }

    receiver ! DoneWithOne
  }
}
