package io.vamp.rest_api

import akka.actor.ActorContext
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.{ InfoBaseRoute, RestApiBase }
import io.vamp.operation.controller.InfoController

trait InfoRoute extends InfoBaseRoute with InfoController {
  this: CommonSupportForActors with RestApiBase ⇒

  def actorContext: ActorContext = context
}
