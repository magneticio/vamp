package io.vamp.core.rest_api

import akka.pattern.ask
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.http.RestApiBase
import io.vamp.common.json.{OffsetDateTimeSerializer, SerializationFormat}
import io.vamp.core.pulse.event.{Aggregator, Event, EventQuery}
import io.vamp.core.pulse.{EventRequestEnvelope, PulseActor}
import org.json4s.Formats
import org.json4s.ext.EnumNameSerializer
import spray.http.StatusCodes._
import spray.httpx.Json4sSupport

trait EventApiRoute extends Json4sSupport {
  this: CommonSupportForActors with RestApiBase =>

  import PulseActor._

  implicit val timeput = PulseActor.timeout
  
  override implicit def json4sFormats: Formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))

  val eventRoutes = path("events" / "get") {
    pathEndOrSingleSlash {
      post {
        pageAndPerPage() { (page, perPage) =>
          entity(as[EventQuery]) { query =>
            onSuccess(actorFor(PulseActor) ? Query(EventRequestEnvelope(query, page, perPage))) { response =>
              respondWith(OK, response)
            }
          }
        }
      }
    }
  } ~
    path("events") {
      pathEndOrSingleSlash {
        post {
          entity(as[Event]) { event =>
            onSuccess(actorFor(PulseActor) ? Publish(event)) { response =>
              respondWith(Created, response)
            }
          }
        }
      }
    }
}

