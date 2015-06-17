package io.vamp.core.model.reader

import io.vamp.core.model.notification.{MissingPathValueError, UndefinedWorkflowTriggerError}
import io.vamp.core.model.workflow._
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
      'trigger(TimeTrigger("15 9 5 1"))
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
    ScheduledWorkflowReader.read(res("workflow/scheduled4.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(DeploymentTrigger("deployment/cluster?create|update|delete"))
    )
  }

  it should "read the time trigger with the higher precedence than event trigger" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled5.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(TimeTrigger("15 9 5 1"))
    )
  }

  it should "fail on no trigger" in {
    expectedError[UndefinedWorkflowTriggerError.type]({
      ScheduledWorkflowReader.read(res("workflow/scheduled6.yml"))
    })
  }

  it should "expand event triggfer tags" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled7.yml")) should have(
      'name("logger-schedule"),
      'workflow(WorkflowReference("logger")),
      'trigger(EventTrigger(Set("deployment")))
    )
  }

  it should "ignore 'script' if 'workflow' is specified" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled8.yml")) should have(
      'name("dead-vamp"),
      'workflow(WorkflowReference("kill-vamp")),
      'trigger(TimeTrigger("0"))
    )
  }

  it should "read anonymous workflow specified with 'script'" in {
    ScheduledWorkflowReader.read(res("workflow/scheduled9.yml")) should have(
      'name("kill-vamp"),
      'workflow(DefaultWorkflow("", "vamp.exit()")),
      'trigger(TimeTrigger("0"))
    )
  }
}
