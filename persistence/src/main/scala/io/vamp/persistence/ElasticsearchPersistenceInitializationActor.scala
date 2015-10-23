package io.vamp.persistence

import io.vamp.persistence.notification.PersistenceNotificationProvider
import io.vamp.pulse.PulseActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor

import scala.io.Source

class ElasticsearchPersistenceInitializationActor extends ElasticsearchInitializationActor with PersistenceNotificationProvider {

  import ElasticsearchPersistenceActor._

  lazy val timeout = PulseActor.timeout

  lazy val elasticsearchUrl = ElasticsearchPersistenceActor.elasticsearchUrl

  lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"elasticsearch/$name.json")).mkString.replace("$NAME", index)
    List("template").map(template ⇒ s"$index-$template" -> load(template)).toMap
  }
}
