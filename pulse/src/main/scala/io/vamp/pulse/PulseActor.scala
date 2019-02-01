package io.vamp.pulse

import io.vamp.common.Config
import io.vamp.common.akka._
import io.vamp.common.http.{ OffsetRequestEnvelope, OffsetResponseEnvelope }
import io.vamp.common.notification.Notification
import io.vamp.model.event._
import io.vamp.model.validator.EventValidator
import io.vamp.pulse.notification._

object EventRequestEnvelope {
  val maxPerPage = 30
}

case class EventRequestEnvelope(request: EventQuery, page: Int, perPage: Int) extends OffsetRequestEnvelope[EventQuery]

case class EventResponseEnvelope(response: List[Event], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Event]

object PulseActor {

  val config = "vamp.pulse"

  val timeout = Config.timeout(s"$config.response-timeout")

  trait PulseMessage

  case class Publish(event: Event, publishEventValue: Boolean = true) extends PulseMessage

  case class Query(query: EventRequestEnvelope) extends PulseMessage

}

trait PulseActor extends PulseFailureNotifier with Percolator with EventValidator with CommonSupportForActors with PulseNotificationProvider {

  implicit val timeout = PulseActor.timeout()

  override def errorNotificationClass = classOf[PulseResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = {
    percolate(publishEventValue = true)(failureNotificationEvent(failure))
    reportException(`class`.getConstructors()(0).newInstance(failure.asInstanceOf[AnyRef]).asInstanceOf[Notification])
  }
}

// PulseActorSupport supports publish and query
trait PulseActorSupport extends PulseActor
// PulseActorPublisher only supports publish
trait PulseActorPublisher extends PulseActor
