package io.vamp.workflow_driver

import akka.actor.ActorRef
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.artifact.Workflow
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.notification.{ WorkflowDriverNotificationProvider, WorkflowResponseError }

import scala.concurrent.Future

object WorkflowDriverActor {

  lazy val timeout = Config.timeout("vamp.workflow-driver.response-timeout")

  sealed trait WorkflowDriveMessage

  case class GetScheduled(workflows: List[Workflow]) extends WorkflowDriveMessage

  case class Schedule(workflow: Workflow, data: Any = None) extends WorkflowDriveMessage

  case class Unschedule(workflow: Workflow) extends WorkflowDriveMessage

}

class WorkflowDriverActor(drivers: List[ActorRef]) extends PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider {

  import WorkflowDriverActor._

  implicit val timeout = WorkflowDriverActor.timeout

  override def errorNotificationClass = classOf[WorkflowResponseError]

  def receive = {
    case InfoRequest     ⇒ reply(info)
    case r: GetScheduled ⇒ drivers.map(_ ? r)
    case r: Schedule     ⇒ reply(Future.sequence(drivers.map(_ ? r)))
    case r: Unschedule   ⇒ reply(Future.sequence(drivers.map(_ ? r)))
    case _               ⇒
  }

  private def info: Future[Map[_, _]] = Future.sequence(drivers.map(_ ? InfoRequest)).map { results ⇒
    results.filter(_.isInstanceOf[Map[_, _]]).map(_.asInstanceOf[Map[_, _]]).reduce(_ ++ _)
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
