package io.vamp.persistence

import io.vamp.persistence.notification.PersistenceNotificationProvider
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition

import scala.io.Source

class ElasticsearchPersistenceInitializationActor extends ElasticsearchInitializationActor with PersistenceNotificationProvider {

  import ElasticsearchPersistenceActor._

  lazy val elasticsearchUrl = ElasticsearchPersistenceActor.elasticsearchUrl

  override lazy val templates: List[TemplateDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"elasticsearch/$name.json")).mkString.replace("$NAME", index)
    List(TemplateDefinition(s"$index-template", load("template")))
  }

  override protected def initializeCustom(): Unit = {
    initializeIndex(index)
    super.initializeCustom()
  }
}
