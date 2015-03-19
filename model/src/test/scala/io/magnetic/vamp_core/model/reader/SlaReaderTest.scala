package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model.artifact._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.concurrent.duration._
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class SlaReaderTest extends FlatSpec with Matchers with ReaderTest {

  "SlaReader" should "read the generic SLA" in {
    SlaReader.read(res("sla1.yml")) should have(
      'name("red"),
      'type("response_time"),
      'parameters(Map("name" -> "red", "window" -> Map("cooldown" -> 600, "interval" -> 600), "threshold" -> Map("lower" -> 100, "upper" -> 1000))),
      'escalations(List(GenericEscalation("", "scale_nothing", Map("scale_by" -> 1, "minimum" -> 1, "maximum" -> 4))))
    )
  }

  it should "read the response time sliding window SLA with generic escalations" in {
    SlaReader.read(res("sla2.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(GenericEscalation("", "scale_nothing", Map("scale_by" -> 1, "minimum" -> 1, "maximum" -> 4))))
    )
  }

  it should "read the response time sliding window SLA with scale escalations" in {
    SlaReader.read(res("sla3.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(ScaleInstancesEscalation("", 1, 4, 1), ScaleCpuEscalation("", 1.0, 4.0, 1.0), ScaleMemoryEscalation("", 1024.0, 2048.5, 512.1)))
    )
  }
}
