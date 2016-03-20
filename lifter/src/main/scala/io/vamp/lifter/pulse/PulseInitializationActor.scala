package io.vamp.lifter.pulse

import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor
import io.vamp.lifter.elasticsearch.ElasticsearchInitializationActor.TemplateDefinition
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.pulse.{ PulseActor, PulseEvent }

import scala.io.Source

class PulseInitializationActor extends PulseEvent with ElasticsearchInitializationActor with LifterNotificationProvider {

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
