package io.vamp.core.pulse_driver.elasticsearch

import akka.actor._
import io.vamp.common.akka._
import io.vamp.core.pulse_driver.notification.PulseDriverNotificationProvider

import scala.language.postfixOps

object ElasticsearchActor extends ActorDescription {

  def props(args: Any*): Props = Props[ElasticsearchActor]

  object StartIndexing

}

class ElasticsearchActor extends CommonSupportForActors with PulseDriverNotificationProvider {

  import ElasticsearchActor._

  def receive: Receive = {

    case StartIndexing => log.info(s"Starting with indexing.")
  }
}
