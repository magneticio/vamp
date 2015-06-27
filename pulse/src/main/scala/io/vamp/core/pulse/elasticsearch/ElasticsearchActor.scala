package io.vamp.core.pulse.elasticsearch

import akka.actor._
import io.vamp.common.akka._
import io.vamp.core.pulse.notification.PulseNotificationProvider

import scala.language.postfixOps

object ElasticsearchActor extends ActorDescription {

  def props(args: Any*): Props = Props[ElasticsearchActor]

  object StartIndexing

}

class ElasticsearchActor extends CommonSupportForActors with PulseNotificationProvider {

  import ElasticsearchActor._

  def receive: Receive = {

    case StartIndexing => log.info(s"Starting with indexing.")
  }
}
