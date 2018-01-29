package io.vamp.common.http

import akka.Done
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, HttpResponse, Uri }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import io.vamp.common.http.EventSource.EventSource

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

private case class SseConnectionConfig(url: String, headers: List[(String, String)], tlsCheck: Boolean)

private case class SseConnectionEntryValue(source: EventSource)

object SseConnector {

  private val retryDelay: FiniteDuration = 5 second
  private val listeners: mutable.Map[SseConnectionConfig, Set[ActorRef]] = mutable.Map()
  private val connections: mutable.Map[SseConnectionConfig, Future[Done]] = mutable.Map()

  def open(url: String, headers: List[(String, String)] = Nil, tlsCheck: Boolean)(actorRef: ActorRef, log: String ⇒ Unit = (_) ⇒ ())(implicit system: ActorSystem): Unit = synchronized {
    val config = SseConnectionConfig(url, headers, tlsCheck)
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    listeners.update(config, listeners.getOrElse(config, Set()) + actorRef)

    connections.getOrElseUpdate(config, {
      log(s"Opening SSE connection: $url")
      EventSource(Uri(url), send(config), None, retryDelay).takeWhile { event ⇒
        event.eventType.foreach(t ⇒ log(s"SSE: $t"))
        val actors = listeners.getOrElse(config, Set())
        actors.foreach(_ ! event)
        val continue = actors.nonEmpty
        if (!continue) log(s"Closing SSE connection: $url")
        continue
      }.runWith(Sink.ignore)
    })
  }

  def close(actorRef: ActorRef): Unit = synchronized {
    listeners.transform((_, v) ⇒ v - actorRef)
  }

  private def send(config: SseConnectionConfig)(request: HttpRequest)(implicit system: ActorSystem, materializer: ActorMaterializer): Future[HttpResponse] = {
    val httpHeaders = config.headers.map { case (k, v) ⇒ HttpHeader.parse(k, v) } collect { case Ok(h, _) ⇒ h } filterNot request.headers.contains
    Source.single(request.withHeaders(request.headers ++ httpHeaders) → 1).via(HttpClient.pool[Any](config.url, config.tlsCheck)).map {
      case (Success(response: HttpResponse), _) ⇒ response
      case (Failure(f), _)                      ⇒ throw new RuntimeException(f.getMessage)
    }.runWith(Sink.head)
  }
}
