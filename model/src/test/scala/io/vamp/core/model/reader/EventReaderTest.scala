package io.vamp.core.model.reader

import java.time.OffsetDateTime

import io.vamp.core.model.event.{Aggregator, TimeRange}
import io.vamp.core.model.notification.{UnsupportedAggregatorError, EventQueryTimeError, MissingPathValueError, NoTagEventError}
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

  "EventQueryReader" should "read the query" in {
    EventQueryReader.read(res("event/query1.yml")) should have(
      'tags(Set("server", "service")),
      'timestamp(Some(TimeRange(None, None, Some("now() - 10m"), None))),
      'aggregator(Some(Aggregator(Aggregator.average, Some("response.time"))))
    )
  }

  it should "expand tags" in {
    EventQueryReader.read(res("event/query2.yml")) should have(
      'tags(Set("server")),
      'timestamp(None),
      'aggregator(None)
    )
  }

  it should "fail on invalid time range" in {
    expectedError[EventQueryTimeError.type]({
      EventQueryReader.read(res("event/query3.yml"))
    })
  }

  it should "fail on unsupported aggregator" in {
    expectedError[UnsupportedAggregatorError]({
      EventQueryReader.read(res("event/query4.yml"))
    })
  }
}
