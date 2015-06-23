package io.vamp.core.pulse_driver

import akka.actor._
import io.vamp.common.akka._
import io.vamp.core.pulse_driver.elasticsearch.{ElasticsearchActor, ElasticsearchInitializationActor}
import io.vamp.core.pulse_driver.notification.PulseDriverNotificationProvider

import scala.io.Source
import scala.language.postfixOps

object PulseDriverInitializationActor extends ActorDescription {

  def props(args: Any*): Props = Props[PulseDriverInitializationActor]
}

class PulseDriverInitializationActor extends ElasticsearchInitializationActor with PulseDriverNotificationProvider {

  override def timeout = PulseDriverActor.timeout

  override def elasticsearchUrl = PulseDriverActor.elasticsearchUrl

  lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"elasticsearch/$name.json")).mkString.replace("$NAME", elasticsearchUrl)
    List("template", "template-event").map(template => s"$elasticsearchUrl-$template" -> load(template)).toMap
  }

  override def done() = {
    actorFor(ElasticsearchActor) ! ElasticsearchActor.StartIndexing
    super.done()
  }
}
