package io.vamp.model.reader

import java.time.OffsetDateTime

import io.vamp.model.notification.{ IllegalPeriod, MissingPathValueError, UndefinedWorkflowTriggerError, UnexpectedElement }
import io.vamp.model.workflow.TimeTrigger.RepeatForever
import io.vamp.model.workflow._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class WorkflowReaderTest extends FlatSpec with Matchers with ReaderTest {

  "WorkflowReader" should "read the workflow" in {
    WorkflowReader.read(res("workflow/workflow1.yml")) should have(
      'name("logger"),
      'script("\nvamp.log(\"hi\")\n")
    )
  }

  it should "not read reference workflow" in {
    expectedError[MissingPathValueError]({
      WorkflowReader.read(res("workflow/workflow2.yml"))
    }) should have(
      'path("script")
    )
  }

  "ScheduledWorkflowReader" should "read the scheduled workflow with time trigger" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled1.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(TimeTrigger("P1Y2M3DT4H5M6S"))
    )
  }

  it should "read the scheduled workflow with deployment trigger" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled2.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(DeploymentTrigger("deployment/cluster?create|update|delete"))
    )
  }

  it should "read the scheduled workflow with an event trigger" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled3.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(EventTrigger(Set("deployment", "cluster")))
    )
  }

  it should "read the deployment trigger with the highest precedence" in {
    expectedError[UnexpectedElement]({
      ScheduledWorkflowReader.read(res("workflow/scheduled4.yml"))
    }) should have(
      'element(Map("period" -> "P1Y2M3DT4H5M6S"))
    )
  }

  it should "read the time trigger with the higher precedence than event trigger" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled5.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(TimeTrigger("P1Y2M3DT4H5M6S"))
    )
  }

  it should "fail on no trigger" in {
    expectedError[UndefinedWorkflowTriggerError.type]({
      ScheduledWorkflowReader.read(res("workflow/scheduled6.yml"))
    })
  }

  it should "expand event trigger tags" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled7.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(EventTrigger(Set("deployment")))
    )
  }

  it should "ignore 'script' if 'workflow' is specified" in {
    expectedError[UnexpectedElement]({
      ScheduledWorkflowReader.read(res("workflow/scheduled8.yml"))
    }) should have(
      'element(Map("script" -> "vamp.exit()"))
    )
  }

  it should "read anonymous workflow specified with 'script'" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled9.yml")) should have(
      'name("kill-vamp"),
      'workflow(DefaultWorkflow("", "vamp.exit()")),
      'trigger(TimeTrigger("P1Y2M3DT4H5M6S"))
    )
  }

  it should "read start time" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled10.yml")) should have(
      'name("kill-vamp"),
      'workflow(DefaultWorkflow("", "vamp.exit()")),
      'trigger(TimeTrigger("P1Y2M3DT4H5M6S", RepeatForever, Option(OffsetDateTime.parse("2007-12-03T08:15:30Z"))))
    )
  }

  it should "read repeat count'" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled11.yml")) should have(
      'name("kill-vamp"),
      'workflow(DefaultWorkflow("", "vamp.exit()")),
      'trigger(TimeTrigger("P1Y2M3DT4H5M6S", 5, Option(OffsetDateTime.parse("2012-10-01T05:52Z"))))
    )
  }

  it should "fail on an invalid period" in {
    expectedError[IllegalPeriod]({
      ScheduledWorkflowReader.read(res("workflow/scheduled12.yml"))
    }) should have(
      'period("123")
    )
  }
}
