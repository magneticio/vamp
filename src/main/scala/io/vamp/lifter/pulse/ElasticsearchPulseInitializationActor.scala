package io.vamp.lifter.pulse

import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor
import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.{ ElasticsearchPulseActor, ElasticsearchPulseEvent }

import scala.io.Source

class ElasticsearchPulseInitializationActor extends ElasticsearchPulseEvent with NamespaceValueResolver with ElasticsearchInitializationActor with LifterNotificationProvider {

  lazy val indexName = resolveWithNamespace(ElasticsearchPulseActor.indexName())

  lazy val indexTimeFormat = ElasticsearchPulseActor.indexTimeFormat()

  lazy val elasticsearchUrl = ElasticsearchPulseActor.elasticsearchUrl()

  override lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", indexName)

    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override protected def initializeCustom(): Unit = {
    initializeIndex(indexTypeName()._1)
    super.initializeCustom()
  }
}
