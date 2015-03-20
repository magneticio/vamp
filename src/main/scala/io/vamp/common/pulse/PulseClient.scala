package io.vamp.common.pulse

import io.vamp.common.http.RestClient
import io.vamp.common.json.Serializers
import io.vamp.common.pulse.api.{Event, EventQuery}
import org.json4s.Formats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class PulseClient(url: String)(implicit val formats: Formats = Serializers.formats) {
  def sendEvent(event: Event): Future[Event] = RestClient.request[Event](s"POST $url/api/v1/events", event)
  def getEvents(eventQuery: EventQuery): Future[Any] = RestClient.request[Any](s"POST $url/api/v1/events/get", eventQuery)
  def resetEvents(): Future[Any] = RestClient.request[Any](s"GET $url/api/v1/events/reset")
}
