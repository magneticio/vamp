package io.vamp.common.http

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, HttpResponse, Uri }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.alpakka.sse.scaladsl.EventSource

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

private case class SseConnectionConfig(url: String, headers: List[(String, String)], tlsCheck: Boolean)

private case class SseConnectionEntryValue(source: EventSource.EventSource)

trait SseListener {
  def onEvent(event: ServerSentEvent): Unit
}

object SseConnector {

  private val retryDelay: FiniteDuration = 5 second
  private val listeners: mutable.Map[SseConnectionConfig, Set[SseListener]] = mutable.Map()
  private val connections: mutable.Map[SseConnectionConfig, Future[Done]] = mutable.Map()

  def open(url: String, headers: List[(String, String)] = Nil, tlsCheck: Boolean)(listener: SseListener)(implicit system: ActorSystem, logger: LoggingAdapter): Unit = synchronized {
    val config = SseConnectionConfig(url, headers, tlsCheck)
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    listeners.update(config, listeners.getOrElse(config, Set()) + listener)

    connections.getOrElseUpdate(config, {
      logger.info(s"Opening SSE connection: $url")
      EventSource(Uri(url), send(config), None, retryDelay).takeWhile { event ⇒
        event.eventType.foreach(t ⇒ logger.info(s"SSE: $t"))
        val receivers = listeners.getOrElse(config, Set())
        receivers.foreach(_.onEvent(event))
        val continue = receivers.nonEmpty
        if (!continue) logger.info(s"Closing SSE connection: $url")
        continue
      }.runWith(Sink.ignore)
    })
  }

  def close(listener: SseListener): Unit = synchronized {
    listeners.transform((_, v) ⇒ v - listener)
  }

  private def send(config: SseConnectionConfig)(request: HttpRequest)(implicit system: ActorSystem, materializer: ActorMaterializer): Future[HttpResponse] = {
    val httpHeaders = config.headers.map { case (k, v) ⇒ HttpHeader.parse(k, v) } collect { case Ok(h, _) ⇒ h } filterNot request.headers.contains
    Source.single(request.withHeaders(request.headers ++ httpHeaders) → 1).via(HttpClient.pool[Any](config.url, config.tlsCheck)).map {
      case (Success(response: HttpResponse), _) ⇒ response
      case (Failure(f), _)                      ⇒ throw new RuntimeException(f.getMessage)
    }.runWith(Sink.head)
  }
}

