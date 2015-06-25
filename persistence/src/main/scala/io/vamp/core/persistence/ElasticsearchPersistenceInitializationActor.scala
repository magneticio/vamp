package io.vamp.core.persistence

import akka.actor.Props
import io.vamp.common.akka.ActorDescription
import io.vamp.core.persistence.notification.PersistenceNotificationProvider
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.elasticsearch.ElasticsearchInitializationActor

import scala.io.Source


object ElasticsearchPersistenceInitializationActor extends ActorDescription {
  def props(args: Any*): Props = Props[ElasticsearchPersistenceInitializationActor]
}

class ElasticsearchPersistenceInitializationActor extends ElasticsearchInitializationActor with PersistenceNotificationProvider {

  import ElasticsearchPersistenceActor._

  lazy val timeout = PulseDriverActor.timeout

  lazy val elasticsearchUrl = ElasticsearchPersistenceActor.elasticsearchUrl

  lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"elasticsearch/$name.json")).mkString.replace("$NAME", index)
    List("template").map(template => s"$index-$template" -> load(template)).toMap
  }
}
