package io.vamp.model.reader

import java.time.OffsetDateTime

import io.vamp.model.artifact._
import io.vamp.model.notification._
import TimeSchedule.{ RepeatCount, RepeatForever }
import io.vamp.model.artifact.DaemonSchedule
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class WorkflowReaderSpec extends FlatSpec with Matchers with ReaderSpec {

  "WorkflowReader" should "read daemon workflow" in {
    WorkflowReader.read(res("workflow/workflow1.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'scale(None),
      'status(Workflow.Status.Active)
    )
  }

  it should "read daemon workflow with default breed" in {
    WorkflowReader.read(res("workflow/workflow2.yml")) should have(
      'name("logger"),
      'breed(DefaultBreed("metrics", Deployable("magneticio/metrics:latest"), Nil, Nil, Nil, Nil, Map())),
      'schedule(DaemonSchedule),
      'scale(None)
    )
  }

  it should "read event based workflow" in {
    WorkflowReader.read(res("workflow/workflow3.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(EventSchedule(Set("a", "b"))),
      'scale(None)
    )
  }

  it should "read event based workflow and expand tags" in {
    WorkflowReader.read(res("workflow/workflow4.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(EventSchedule(Set("a"))),
      'scale(None)
    )
  }

  it should "read event based workflow and expand event if list" in {
    WorkflowReader.read(res("workflow/workflow5.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(EventSchedule(Set("a", "b"))),
      'scale(None)
    )
  }

  it should "read event based workflow and expand event if tag" in {
    WorkflowReader.read(res("workflow/workflow6.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(EventSchedule(Set("a"))),
      'scale(None)
    )
  }

  it should "read time based workflow" in {
    WorkflowReader.read(res("workflow/workflow7.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(TimeSchedule("P1Y2M3DT4H5M6S")),
      'scale(None)
    )
  }

  it should "read time based workflow and expand time" in {
    WorkflowReader.read(res("workflow/workflow8.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(TimeSchedule("P1Y2M3DT4H5M6S")),
      'scale(None)
    )
  }

  it should "read time based workflow with repeat" in {
    WorkflowReader.read(res("workflow/workflow9.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(TimeSchedule("P1Y2M3DT4H5M6S", RepeatCount(5))),
      'scale(None)
    )
  }

  it should "read time based workflow with start time" in {
    WorkflowReader.read(res("workflow/workflow10.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(TimeSchedule("P1Y2M3DT4H5M6S", RepeatForever, Option(OffsetDateTime.parse("2007-12-03T08:15:30Z")))),
      'scale(None)
    )
  }

  it should "read time based workflow with repeat and start time" in {
    WorkflowReader.read(res("workflow/workflow11.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(TimeSchedule("P1Y2M3DT4H5M6S", RepeatCount(10), Option(OffsetDateTime.parse("2007-12-03T08:15:30Z")))),
      'scale(None)
    )
  }

  it should "read daemon workflow with scale" in {
    WorkflowReader.read(res("workflow/workflow12.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'scale(Some(DefaultScale("", Quantity(1), MegaByte(128), 2)))
    )
  }

  it should "read daemon workflow with scale reference" in {
    WorkflowReader.read(res("workflow/workflow13.yml")) should have(
      'name("logger"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'scale(Some(ScaleReference("small")))
    )
  }

  it should "fail if no schedule" in {
    expectedError[UndefinedWorkflowScheduleError.type]({
      WorkflowReader.read(res("workflow/workflow14.yml"))
    })
  }

  it should "fail if invalid period" in {
    expectedError[IllegalWorkflowSchedulePeriod]({
      WorkflowReader.read(res("workflow/workflow15.yml"))
    }) should have(
      'period("now")
    )
  }

  it should "fail if invalid start time" in {
    expectedError[UnexpectedTypeError]({
      WorkflowReader.read(res("workflow/workflow16.yml"))
    }) should have(
      'path("start")
    )
  }

  it should "read network" in {
    WorkflowReader.read(res("workflow/workflow17.yml")) should have(
      'name("metrics"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'network(Some("managed"))
    )
  }

  it should "read arguments" in {
    WorkflowReader.read(res("workflow/workflow18.yml")) should have(
      'name("metrics"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'arguments(List(Argument("test", "abcd")))
    )
  }

  it should "read expand arguments" in {
    WorkflowReader.read(res("workflow/workflow19.yml")) should have(
      'name("metrics"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'arguments(List(Argument("test", "abcd")))
    )
  }

  it should "read fail on non boolean privileged argument" in {
    expectedError[InvalidArgumentValueError]({
      WorkflowReader.read(res("workflow/workflow20.yml"))
    }) should have(
      'argument(Argument("privileged", "abcd"))
    )
  }

  it should "read environment variables" in {
    WorkflowReader.read(res("workflow/workflow21.yml")) should have(
      'name("metrics"),
      'breed(BreedReference("metrics")),
      'schedule(DaemonSchedule),
      'environmentVariables(List(EnvironmentVariable("HEAP", None, Some("1024MB"), None)))
    )
  }

  it should "fail on no environment variable value" in {
    expectedError[MissingEnvironmentVariableError]({
      WorkflowReader.read(res("workflow/workflow22.yml"))
    }) should have(
      'breed(DefaultBreed("metrics", Deployable("container/docker", "metrics"), Nil, List(EnvironmentVariable("HEAP", None, None, None)), Nil, Nil, Map())),
      'name("HEAP")
    )
  }

  it should "fail on non existing environment variable" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      WorkflowReader.read(res("workflow/workflow23.yml"))
    }) should have(
      'breed(DefaultBreed("metrics", Deployable("container/docker", "metrics"), Nil, List(EnvironmentVariable("HEAP", None, Option("128MB"), None)), Nil, Nil, Map())),
      'reference("THEME")
    )
  }

  it should "read active status" in {
    WorkflowReader.read(res("workflow/workflow24.yml")) should have(
      'name("logger"),
      'status(Workflow.Status.Active)
    )
  }

  it should "read suspended status" in {
    WorkflowReader.read(res("workflow/workflow25.yml")) should have(
      'name("logger"),
      'status(Workflow.Status.Suspended)
    )
  }

  it should "read restarting status" in {
    WorkflowReader.read(res("workflow/workflow26.yml")) should have(
      'name("logger"),
      'status(Workflow.Status.Restarting(Workflow.Status.RestartingPhase.Stopping))
    )
  }

  it should "read restarting status and phase stopping" in {
    WorkflowReader.read(res("workflow/workflow27.yml")) should have(
      'name("logger"),
      'status(Workflow.Status.Restarting(Workflow.Status.RestartingPhase.Stopping))
    )
  }

  it should "read restarting status and phase starting" in {
    WorkflowReader.read(res("workflow/workflow28.yml")) should have(
      'name("logger"),
      'status(Workflow.Status.Restarting(Workflow.Status.RestartingPhase.Starting))
    )
  }

  it should "fail on non existing status" in {
    expectedError[IllegalWorkflowStatus]({
      WorkflowReader.read(res("workflow/workflow29.yml"))
    }) should have(
      'status("running")
    )
  }

  it should "fail on non existing status phase" in {
    expectedError[IllegalWorkflowStatusPhase]({
      WorkflowReader.read(res("workflow/workflow30.yml"))
    }) should have(
      'phase("complete")
    )
  }
}
