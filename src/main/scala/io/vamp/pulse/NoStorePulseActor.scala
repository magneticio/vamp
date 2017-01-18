package io.vamp.pulse

import io.vamp.common.spi.ClassMapper
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.model.event._
import io.vamp.pulse.Percolator.{ GetPercolator, RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.notification._

import scala.concurrent.Future

class NoStorePulseActorMapper extends ClassMapper {
  val name = "no-store"
  val clazz = classOf[NoStorePulseActor]
}

class NoStorePulseActor extends PulseActor {

  import PulseActor._

  def receive = {

    case InfoRequest ⇒ reply(Future.successful(Map[String, Any]("type" → "no-store")))

    case StatsRequest ⇒ reply(Future.successful(None))

    case Publish(event, publishEventValue) ⇒ reply((validateEvent andThen percolate(publishEventValue) andThen publish(publishEventValue))(Event.expandTags(event)), classOf[EventIndexError])

    case Query(envelope) ⇒ reply((validateEventQuery andThen eventQuery(envelope.page, envelope.perPage))(envelope.request), classOf[EventQueryError])

    case GetPercolator(name) ⇒ reply(Future.successful(getPercolator(name)))

    case RegisterPercolator(name, tags, kind, message) ⇒ registerPercolator(name, tags, kind, message)

    case UnregisterPercolator(name) ⇒ unregisterPercolator(name)

    case any ⇒ unsupported(UnsupportedPulseRequest(any))
  }

  private def publish(publishEventValue: Boolean)(event: Event) = Future.successful(event)

  protected def eventQuery(page: Int, perPage: Int)(query: EventQuery): Future[Any] = {
    log.debug(s"Pulse query: $query")
    query.aggregator match {
      case None                                    ⇒ Future.successful(EventResponseEnvelope(Nil, 0, page, perPage))
      case Some(Aggregator(Aggregator.`count`, _)) ⇒ Future.successful(LongValueAggregationResult(0))
      case Some(Aggregator(_, _))                  ⇒ Future.successful(DoubleValueAggregationResult(0))
      case _                                       ⇒ throw new UnsupportedOperationException
    }
  }
}
