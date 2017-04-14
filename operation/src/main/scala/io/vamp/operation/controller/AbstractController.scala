package io.vamp.operation.controller

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider

trait AbstractController extends ActorSystemProvider with ExecutionContextProvider with NotificationProvider
