package io.vamp.workflow_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverRequest, WorkflowDriverNotificationProvider, WorkflowResponseError }

import scala.concurrent.Future
import scala.concurrent.duration._

object WorkflowDriverActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.workflow-driver.response-timeout").seconds)

  sealed trait WorkflowDriveMessage

  object Scheduled extends WorkflowDriveMessage

  case class Schedule(scheduledWorkflow: ScheduledWorkflow, data: Any = None) extends WorkflowDriveMessage

  case class Unschedule(scheduledWorkflow: ScheduledWorkflow) extends WorkflowDriveMessage

}

class WorkflowDriverActor(drivers: List[WorkflowDriver]) extends PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider {

  import WorkflowDriverActor._

  implicit val timeout = WorkflowDriverActor.timeout

  override def errorNotificationClass = classOf[WorkflowResponseError]

  def receive = {
    case InfoRequest                       ⇒ reply(info)
    case Scheduled                         ⇒ reply(all())
    case Schedule(scheduledWorkflow, data) ⇒ reply(schedule(data)(scheduledWorkflow))
    case Unschedule(scheduledWorkflow)     ⇒ reply(unschedule()(scheduledWorkflow))
    case any                               ⇒ unsupported(UnsupportedWorkflowDriverRequest(any))
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def info: Future[Map[_, _]] = Future.sequence(drivers.map(_.info)).map(_.reduce(_ ++ _))

  private def all(): Future[List[WorkflowInstance]] = Future.sequence(drivers.map(_.all())).map(_.reduce(_ ++ _))

  private def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = drivers.map(_.schedule(data)).reduce(_ orElse _)

  private def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = drivers.map(_.unschedule()).reduce(_ orElse _)
}
