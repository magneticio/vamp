package io.magnetic.vamp_common.pulse

import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_common.pulse.api.{EventQuery, Event}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global



class PulseClient(url: String) {
  def sendEvent(event: Event): Future[Any] = RestClient.request[Any](s"POST $url/v1/events", event)
  def getEvents(eventQuery: EventQuery): Future[Any] = RestClient.request[Any](s"POST $url/v1/events/get", eventQuery)
}
