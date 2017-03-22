package io.vamp.lifter.persistence

import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor
import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.persistence.ElasticsearchPersistenceActor

import scala.io.Source

class ElasticsearchPersistenceInitializationActor extends ElasticsearchInitializationActor with NamespaceValueResolver with LifterNotificationProvider {

  lazy val index = resolveWithNamespace(ElasticsearchPersistenceActor.index())

  lazy val elasticsearchUrl = ElasticsearchPersistenceActor.elasticsearchUrl()

  override lazy val templates: List[TemplateDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", index)

    List(TemplateDefinition(s"$index-template", load("template")))
  }

  override protected def initializeCustom(): Unit = {
    initializeIndex(index)
    super.initializeCustom()
  }
}
