package io.magnetic.vamp_common.notification

import org.scalatest.{Matchers, WordSpecLike}

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
