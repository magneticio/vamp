package io.magnetic.vamp_common.pulse

import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_common.json.Serializers
import io.magnetic.vamp_common.pulse.api.{EventQuery, Event}
import org.json4s.Formats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global



class PulseClient(url: String)(implicit val formats: Formats = Serializers.formats) {
  def sendEvent(event: Event): Future[Any] = RestClient.request[Any](s"POST $url/api/v1/events", event)
  def getEvents(eventQuery: EventQuery): Future[Any] = RestClient.request[Any](s"POST $url/api/v1/events/get", eventQuery)
}
