package io.vamp.common.akka

import akka.actor.{Actor, ActorLogging, ActorSystem}
import io.vamp.common.notification.NotificationProvider

trait CommonSupportForActors
  extends Actor
  with ActorLogging
  with ActorSystemProvider
  with ActorExecutionContextProvider
  with ReplyActor
  with NotificationProvider {
  implicit def actorSystem: ActorSystem = context.system
}
