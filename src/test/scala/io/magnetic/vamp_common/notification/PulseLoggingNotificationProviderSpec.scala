package io.magnetic.vamp_common.notification

import akka.actor.Actor.Receive
import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike, FlatSpec}

case class TestNotification(id: Int, name: String) extends Notification

class PulseLoggingNotificationProviderSpec extends WordSpecLike with Matchers
with PulseLoggingNotificationProvider with DefaultPackageMessageResolverProvider
with DefaultTagResolverProvider{

  override protected val url: String = "http://localhost:8083"

  /**
   * Prerequisite for pulse notification provider testing is running instance of pulse and correct path to it
   *
   */
  "Pulse notification" must {
    "Be successfully sent" in {
        info(TestNotification(1, "test"))
    }
  }
}
