package io.vamp.common.akka

import akka.actor.{Actor, ActorLogging}
import io.vamp.common.notification.NotificationProvider

trait CommonSupportForActors
  extends Actor
  with ActorLogging
  with ActorExecutionContextProvider
  with ReplyActor
  with NotificationProvider
