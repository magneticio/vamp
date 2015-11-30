package io.vamp.pulse.elasticsearch

import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.pulse.notification.PulseNotificationProvider
import io.vamp.pulse.{ PulseActor, PulseEvent }

import scala.io.Source

class PulseInitializationActor extends PulseEvent with ElasticsearchInitializationActor with PulseNotificationProvider {

  import PulseActor._

  lazy val elasticsearchUrl = PulseActor.elasticsearchUrl

  override lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", indexName)
    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override protected def initializeCustom(): Unit = {
    initializeIndex(indexTypeName()._1)
    super.initializeCustom()
  }
}
