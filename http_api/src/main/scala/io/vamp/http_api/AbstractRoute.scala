package io.vamp.http_api

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider

trait AbstractRoute extends ActorSystemProvider with ExecutionContextProvider with NotificationProvider
