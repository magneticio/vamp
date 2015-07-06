package io.vamp.core.rest_api

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ActorDescription
import io.vamp.common.http.HttpServerBaseActor
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.rest_api.notification.RestApiNotificationProvider
import org.json4s.Formats

import scala.concurrent.duration._

object HttpServerActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.rest-api.response-timeout").seconds)

  def props(args: Any*): Props = Props[HttpServerActor]
}

class HttpServerActor extends HttpServerBaseActor with RestApiRoute with RestApiNotificationProvider {

  implicit val timeout = HttpServerActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default
}
