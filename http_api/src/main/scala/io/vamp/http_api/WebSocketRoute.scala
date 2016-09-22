package io.vamp.http_api

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.controller.WebSocketController

trait WebSocketRoute extends WebSocketController {
  this: HttpApiDirectives with ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  val websocketRoute = get {
    handleWebSocketMessages {
      websocket
    }
  }
}
