package io.vamp.core.pulse.elasticsearch

import akka.actor._
import io.vamp.common.akka._
import io.vamp.core.pulse.PulseActor
import io.vamp.core.pulse.notification.PulseNotificationProvider

import scala.io.Source
import scala.language.postfixOps

object PulseInitializationActor extends ActorDescription {

  def props(args: Any*): Props = Props[PulseInitializationActor]
}

class PulseInitializationActor extends ElasticsearchInitializationActor with PulseNotificationProvider {

  lazy val timeout = PulseActor.timeout

  lazy val elasticsearchUrl = PulseActor.elasticsearchUrl

  lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", PulseActor.indexName)
    List("template", "template-event").map(template => s"${PulseActor.indexName}-$template" -> load(template)).toMap
  }
}
