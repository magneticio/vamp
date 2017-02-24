package io.vamp.common.akka

import io.vamp.common.NamespaceProvider
import io.vamp.common.notification.NotificationProvider

trait CommonProvider extends NamespaceProvider with ActorSystemProvider with ExecutionContextProvider with NotificationProvider
