package io.vamp.persistence.refactor.dao

import akka.actor.{Actor, Props, Stash}
import akka.event.Logging
/**
  * Created by mihai on 1/12/18.
  */

class SimpleLock extends Actor with Stash {
  val log = Logging(context.system, this)

  def receive = receiveWhenUnlocked

  def receiveWhenLocked: Receive = {
    case ReleaseLock => {
      unstashAll()
      context.become(receiveWhenUnlocked)
      sender ! Ack
    }
    case msg => {
      stash()
    }
  }

  def receiveWhenUnlocked: Receive = {
    case GetLock => {
      unstashAll()
      context.become(receiveWhenLocked)
      sender ! Ack
    }
    case msg => {
      stash()
    }
  }
}

case object GetLock
case object ReleaseLock
case object Ack