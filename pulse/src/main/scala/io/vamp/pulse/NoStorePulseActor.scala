package io.vamp.pulse

import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.model.event._
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.notification._

import scala.concurrent.Future

class NoStorePulseActor extends PulseActor {

  import PulseActor._

  def receive = {

    case InfoRequest                             ⇒ reply(Future.successful(Map[String, Any]("type" → "no-store")))

    case StatsRequest                            ⇒ reply(Future.successful(None))

    case Publish(event, publishEventValue)       ⇒ reply((validateEvent andThen percolate(publishEventValue) andThen publish(publishEventValue))(Event.expandTags(event)), classOf[EventIndexError])

    case Query(envelope)                         ⇒ reply((validateEventQuery andThen eventQuery(envelope.page, envelope.perPage))(envelope.request), classOf[EventQueryError])

    case RegisterPercolator(name, tags, message) ⇒ registerPercolator(name, tags, message)

    case UnregisterPercolator(name)              ⇒ unregisterPercolator(name)

    case any                                     ⇒ unsupported(UnsupportedPulseRequest(any))
  }

  private def publish(publishEventValue: Boolean)(event: Event) = Future.successful(event)

  protected def eventQuery(page: Int, perPage: Int)(query: EventQuery): Future[Any] = {
    log.debug(s"Pulse query: $query")
    query.aggregator match {
      case None                                    ⇒ Future.successful(EventResponseEnvelope(Nil, 0, page, perPage))
      case Some(Aggregator(Aggregator.`count`, _)) ⇒ Future.successful(LongValueAggregationResult(0))
      case Some(Aggregator(aggregator, field))     ⇒ Future.successful(DoubleValueAggregationResult(0))
      case _                                       ⇒ throw new UnsupportedOperationException
    }
  }
}
