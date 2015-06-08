package io.vamp.common.akka

import akka.actor.{Actor, ActorLogging}
import io.vamp.common.notification.NotificationProvider


trait CommonActorSupport
  extends Actor
  with ActorLogging
  with ActorSupport
  with FutureSupport
  with ActorExecutionContextProvider
  with NotificationProvider
