package io.vamp.workflow_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverRequest, WorkflowDriverNotificationProvider, WorkflowResponseError }

import scala.concurrent.duration._

object WorkflowDriverActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.workflow-driver.response-timeout").seconds)

  sealed trait WorkflowDriveMessage

  object All extends WorkflowDriveMessage

  case class Schedule(scheduledWorkflow: ScheduledWorkflow) extends WorkflowDriveMessage

  case class Unschedule(scheduledWorkflow: ScheduledWorkflow) extends WorkflowDriveMessage

}

class WorkflowDriverActor(driver: WorkflowDriver) extends PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider {

  import WorkflowDriverActor._

  implicit val timeout = WorkflowDriverActor.timeout

  override def errorNotificationClass = classOf[WorkflowResponseError]

  def receive = {
    case InfoRequest                   ⇒ reply(driver.info)
    case All                           ⇒ reply(driver.all)
    case Schedule(scheduledWorkflow)   ⇒ reply(driver.schedule(scheduledWorkflow))
    case Unschedule(scheduledWorkflow) ⇒ reply(driver.unschedule(scheduledWorkflow))
    case any                           ⇒ unsupported(UnsupportedWorkflowDriverRequest(any))
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

