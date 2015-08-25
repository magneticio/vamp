package io.vamp.core.operation.sse

import java.util.concurrent.TimeUnit

import io.vamp.common.akka.Bootstrap.Shutdown
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.core.model.event.Event._
import io.vamp.core.model.reader.EventReader
import io.vamp.core.operation.notification.OperationNotificationProvider
import io.vamp.core.pulse.Percolator
import io.vamp.core.pulse.Percolator.{RegisterPercolator, UnregisterPercolator}
import org.glassfish.jersey.client.JerseyClientBuilder
import org.glassfish.jersey.media.sse.{EventListener, EventSource, InboundEvent}


class SseConsumerActor extends Percolator with CommonSupportForActors with OperationNotificationProvider {

  private val streamUrl = context.system.settings.config.getString("vamp.core.rest-api.sse.router-stream")

  private var eventSource: Option[EventSource] = None

  protected override val logMatch = false

  def receive: Receive = {

    case RegisterPercolator(name, tags, message) =>
      if (percolators.isEmpty) startConsuming()
      registerPercolator(name, tags, message)

    case UnregisterPercolator(name) =>
      unregisterPercolator(name)
      if (percolators.isEmpty) stopConsuming()

    case Shutdown => stopConsuming()

    case _ =>
  }

  private def startConsuming() = {
    stopConsuming()
    eventSource = Some(EventSource.target(JerseyClientBuilder.createClient.target(streamUrl)).reconnectingEvery(500, TimeUnit.MILLISECONDS).build())
    eventSource.foreach { es =>
      es.register(new EventListener {
        override def onEvent(inboundEvent: InboundEvent): Unit = try {
          (expandTags andThen percolate)(EventReader.read(inboundEvent.readData))
        } catch {
          case e: Throwable => log.debug(s"Can't process: ${inboundEvent.toString}")
        }
      })
      log.info(s"Opening SSE connection to Vamp Router.")
      es.open()
    }
  }

  private def stopConsuming() = eventSource.foreach { es =>
    if (es.isOpen) {
      log.info(s"Closing SSE connection to Vamp Router.")
      es.close()
    }
  }
}

