package io.vamp.model.reader

import io.vamp.model.artifact._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import io.vamp.common.{ RestrictedInt, RestrictedMap, RootAnyMap }

import scala.concurrent.duration._
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class SlaReaderSpec extends FlatSpec with Matchers with ReaderSpec {

  "SlaReader" should "read the generic SLA" in {
    SlaReader.read(res("sla/sla1.yml")) should have(
      'name("red"),
      'type("response_time"),
      'parameters(RootAnyMap(Map("window" → RestrictedMap(Map("interval" → RestrictedInt(600), "cooldown" → RestrictedInt(600))), "threshold" → RestrictedMap(Map("lower" → RestrictedInt(100), "upper" → RestrictedInt(1000)))))),
      'escalations(List(GenericEscalation("", RootAnyMap.empty, "scale_nothing", RootAnyMap(Map("scale_by" → RestrictedInt(1), "minimum" → RestrictedInt(1), "maximum" → RestrictedInt(4))))))
    )
  }

  it should "read the response time sliding window SLA with generic escalations" in {
    SlaReader.read(res("sla/sla2.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(GenericEscalation("", RootAnyMap.empty, "scale_nothing", RootAnyMap(Map("scale_by" → RestrictedInt(1), "minimum" → RestrictedInt(1), "maximum" → RestrictedInt(4))))))
    )
  }

  it should "read the response time sliding window SLA with scale escalations" in {
    SlaReader.read(res("sla/sla3.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(ToAllEscalation("", RootAnyMap.empty, List(ScaleInstancesEscalation("", RootAnyMap.empty, 1, 4, 1, None), ScaleCpuEscalation("", RootAnyMap.empty, 1.0, 4.0, 1.0, None), ScaleMemoryEscalation("", RootAnyMap.empty, 1024.0, 2048.5, 512.1, None)))))
    )
  }

  it should "read the SLA with a group escalation" in {
    SlaReader.read(res("sla/sla4.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(ToAllEscalation("", RootAnyMap.empty, List(EscalationReference("notify"), ToOneEscalation("", RootAnyMap.empty, List(ScaleInstancesEscalation("", RootAnyMap.empty, 1, 4, 1, None), ScaleCpuEscalation("", RootAnyMap.empty, 1.0, 4.0, 1.0, None)))))))
    )
  }

  it should "read the SLA with a group escalation with expansion" in {
    SlaReader.read(res("sla/sla5.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(ToAllEscalation("", RootAnyMap.empty, List(EscalationReference("notify"), ToOneEscalation("", RootAnyMap.empty, List(ScaleInstancesEscalation("", RootAnyMap.empty, 1, 4, 1, None), ScaleCpuEscalation("", RootAnyMap.empty, 1.0, 4.0, 1.0, None)))))))
    )
  }

  it should "read the SLA with a nested group escalation" in {
    SlaReader.read(res("sla/sla6.yml")) should have(
      'name("red"),
      'interval(600 seconds),
      'cooldown(600 seconds),
      'upper(1000 milliseconds),
      'lower(100 milliseconds),
      'escalations(List(ToAllEscalation("", RootAnyMap.empty, List(EscalationReference("notify"), ToOneEscalation("", RootAnyMap.empty, List(ScaleInstancesEscalation("", RootAnyMap.empty, 1, 4, 1, None), ToAllEscalation("", RootAnyMap.empty, List(ScaleCpuEscalation("", RootAnyMap.empty, 1.0, 4.0, 1.0, None), EscalationReference("email")))))))))
    )
  }
}
