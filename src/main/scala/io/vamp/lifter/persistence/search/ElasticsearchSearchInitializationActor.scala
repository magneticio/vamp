package io.vamp.lifter.persistence.search

import io.vamp.common.{Config, ConfigMagnet}
import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor
import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.lifter.persistence.search.ElasticsearchSearchInitializationActor._

import scala.io.Source

object ElasticsearchSearchInitializationActor {

  val searchUrl: ConfigMagnet[String] = Config.string("vamp.persistence.search.es.url")

  val searchIndex: ConfigMagnet[String] = Config.string("vamp.persistence.search.es.index.name")

  val indexTimeFormat: ConfigMagnet[String] = Config.string("vamp.persistence.es.index.time-format.event")

}

/**
  * Initializes the needed parts for searching artifacts via ES
  */
class ElasticsearchSearchInitializationActor extends NamespaceValueResolver
  with ElasticsearchInitializationActor
  with LifterNotificationProvider {

  lazy val indexName = resolveWithNamespace(searchIndex())

  lazy val elasticsearchUrl = searchUrl()

  override lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", indexName)

    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override protected def initializeCustom(): Unit = {
    initializeIndex(indexName)
  }

}
