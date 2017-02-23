package io.vamp.common.akka

import io.vamp.common.NamespaceResolverProvider
import io.vamp.common.notification.NotificationProvider

trait CommonProvider extends NamespaceResolverProvider with ActorSystemProvider with ExecutionContextProvider with NotificationProvider
