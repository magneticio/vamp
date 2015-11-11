package io.vamp.pulse.elasticsearch

import io.vamp.pulse.PulseActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.pulse.notification.PulseNotificationProvider

import scala.io.Source

class PulseInitializationActor extends ElasticsearchInitializationActor with PulseNotificationProvider {

  lazy val elasticsearchUrl = PulseActor.elasticsearchUrl

  override lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", PulseActor.indexName)
    List("template", "template-event").map(template ⇒ TemplateDefinition(s"${PulseActor.indexName}-$template", load(template)))
  }
}
