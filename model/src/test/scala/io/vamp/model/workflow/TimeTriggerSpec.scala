package io.vamp.model.workflow

import java.time.{ Duration, Period }

import io.vamp.model.reader.ReaderTest
import io.vamp.model.workflow.TimeTrigger.{ RepeatForever, RepeatPeriod }
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class TimeTriggerSpec extends FlatSpec with Matchers with ReaderTest {

  "TimeTrigger" should "read an empty period" in {
    TimeTrigger("") should have(
      'period(RepeatPeriod(None, None)),
      'repeatTimes(RepeatForever),
      'startTime(None)
    )
  }

  it should "read days" in {
    TimeTrigger("P1Y2M3D") should have(
      'period(RepeatPeriod(Some(Period.parse("P1Y2M3D")), None)),
      'repeatTimes(RepeatForever),
      'startTime(None)
    )
  }

  it should "read time" in {
    TimeTrigger("PT1H2M3S") should have(
      'period(RepeatPeriod(None, Some(Duration.parse("PT1H2M3S")))),
      'repeatTimes(RepeatForever),
      'startTime(None)
    )
  }

  it should "read days and time" in {
    TimeTrigger("P1Y2M3DT1H2M3S") should have(
      'period(RepeatPeriod(Some(Period.parse("P1Y2M3D")), Some(Duration.parse("PT1H2M3S")))),
      'repeatTimes(RepeatForever),
      'startTime(None)
    )
  }
}
