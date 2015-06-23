package io.vamp.core.pulse_driver

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.core.pulse_driver.elasticsearch.{ElasticsearchActor, ElasticsearchInitializationActor}
import io.vamp.core.pulse_driver.notification.PulseDriverNotificationProvider

import scala.io.Source
import scala.language.postfixOps

object PulseDriverInitializationActor extends ActorDescription {

  def props(args: Any*): Props = Props[PulseDriverInitializationActor]
}

class PulseDriverInitializationActor extends ElasticsearchInitializationActor with PulseDriverNotificationProvider {

  lazy val timeout = PulseDriverActor.timeout

  lazy val elasticsearchUrl = PulseDriverActor.elasticsearchUrl

  private lazy val indexPrefix = ConfigFactory.load().getString("vamp.core.pulse-driver.elasticsearch.index-prefix")

  lazy val templates = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"elasticsearch/$name.json")).mkString.replace("$NAME", indexPrefix)
    List("template", "template-event").map(template => s"$indexPrefix-$template" -> load(template)).toMap
  }

  override def done() = {
    actorFor(ElasticsearchActor) ! ElasticsearchActor.StartIndexing
    super.done()
  }
}
