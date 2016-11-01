package io.vamp.model.workflow

import java.time.{ Duration, Period }

import io.vamp.model.artifact.TimeSchedule
import io.vamp.model.reader.ReaderSpec
import io.vamp.model.artifact.TimeSchedule.{ RepeatForever, RepeatPeriod }
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TimeScheduleSpec extends FlatSpec with Matchers with ReaderSpec {

  "TimeSchedule" should "read an empty period" in {
    TimeSchedule("") should have(
      'period(RepeatPeriod(None, None)),
      'repeat(RepeatForever),
      'start(None)
    )
  }

  it should "read days" in {
    TimeSchedule("P1Y2M3D") should have(
      'period(RepeatPeriod(Some(Period.parse("P1Y2M3D")), None)),
      'repeat(RepeatForever),
      'start(None)
    )
  }

  it should "read time" in {
    TimeSchedule("PT1H2M3S") should have(
      'period(RepeatPeriod(None, Some(Duration.parse("PT1H2M3S")))),
      'repeat(RepeatForever),
      'start(None)
    )
  }

  it should "read days and time" in {
    TimeSchedule("P1Y2M3DT1H2M3S") should have(
      'period(RepeatPeriod(Some(Period.parse("P1Y2M3D")), Some(Duration.parse("PT1H2M3S")))),
      'repeat(RepeatForever),
      'start(None)
    )
  }
}
