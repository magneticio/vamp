package io.vamp.rest_api

import io.vamp.common.config.Config
import io.vamp.common.http.HttpServerBaseActor
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.rest_api.notification.RestApiNotificationProvider
import org.json4s.Formats

object HttpServerActor {

  lazy val timeout = Config.timeout("vamp.rest-api.response-timeout")

}

class HttpServerActor extends HttpServerBaseActor with RestApiRoute with RestApiNotificationProvider {

  override def actorRefFactory = super[HttpServerBaseActor].actorRefFactory

  implicit val timeout = HttpServerActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default
}
