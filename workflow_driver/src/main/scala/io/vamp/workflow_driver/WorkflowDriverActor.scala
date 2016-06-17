package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.notification.{ WorkflowDriverNotificationProvider, WorkflowResponseError }

import scala.concurrent.Future

object WorkflowDriverActor {

  lazy val timeout = Config.timeout("vamp.workflow-driver.response-timeout")

  sealed trait WorkflowDriveMessage

  case class GetScheduled(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]) extends WorkflowDriveMessage

  case class Scheduled(scheduledWorkflow: ScheduledWorkflow, workflowInstance: Option[WorkflowInstance]) extends WorkflowDriveMessage

  case class Schedule(scheduledWorkflow: ScheduledWorkflow, data: Any = None) extends WorkflowDriveMessage

  case class Unschedule(scheduledWorkflow: ScheduledWorkflow) extends WorkflowDriveMessage

}

class WorkflowDriverActor(drivers: List[WorkflowDriver]) extends PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider {

  import WorkflowDriverActor._

  implicit val timeout = WorkflowDriverActor.timeout

  override def errorNotificationClass = classOf[WorkflowResponseError]

  def receive = {
    case InfoRequest                      ⇒ reply(info)
    case GetScheduled(replyTo, workflows) ⇒ get(replyTo, workflows)
    case Schedule(workflow, data)         ⇒ reply(schedule(data)(workflow))
    case Unschedule(workflow)             ⇒ reply(unschedule()(workflow))
    case _                                ⇒
  }

  private def info: Future[Map[_, _]] = Future.sequence(drivers.map(_.info)).map(_.reduce(_ ++ _))

  private def get(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]) = drivers.foreach(_.request(replyTo, scheduledWorkflows))

  private def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = drivers.map(_.schedule(data)).reduce(_ orElse _)

  private def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = drivers.map(_.unschedule()).reduce(_ orElse _)

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
