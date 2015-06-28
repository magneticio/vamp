package io.vamp.core.model.reader

import java.time.OffsetDateTime

import io.vamp.core.model.notification.{MissingPathValueError, NoTagEventError}
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class EventReaderTest extends FlatSpec with Matchers with ReaderTest {

  "EventReader" should "read the event" in {
    EventReader.read(res("event/event1.yml")) should have(
      'tags(Set("server", "service")),
      'timestamp(OffsetDateTime.parse("2015-06-05T15:12:38.000Z")),
      'value(0),
      'type("metrics")
    )
  }

  it should "expand tags" in {
    EventReader.read(res("event/event2.yml")) should have(
      'tags(Set("server")),
      'value(Map("response" -> Map("time" -> 50))),
      'type("metrics")
    )
  }

    it should "fail on no tag" in {
      expectedError[MissingPathValueError]({
        EventReader.read(res("event/event3.yml"))
      })
    }

  it should "fail on empty tags" in {
    expectedError[NoTagEventError.type]({
      EventReader.read(res("event/event4.yml"))
    })
  }

}
