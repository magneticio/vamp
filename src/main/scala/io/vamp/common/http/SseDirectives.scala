package io.vamp.common.http

import akka.actor._
import spray.can.Http
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.http._
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

/**
 * @see https://github.com/siriux/spray_sse
 */
trait SseDirectives {

  case class SseMessage(event: Option[String], data: String)

  case class RegisterClosedHandler(handler: () => Unit)

  object CloseConnection

  val `text/event-stream` = register(MediaType.custom(mainType = "text", subType = "event-stream", compressible = true, binary = true))

  def responseStart = HttpResponse(
    headers = `Cache-Control`(CacheDirectives.`no-cache`) ::
      RawHeader("Access-Control-Allow-Methods", "GET, POST") ::
      RawHeader("Access-Control-Allow-Headers", "Cache-Control") ::
      RawHeader("Access-Control-Max-Age", "86400") ::
      Nil,
    entity = ":\n"
  )

  def sseKeepAliveTimeout = 15 seconds

  def registerClosedHandler(channel: ActorRef, handler: () => Unit) = channel ! RegisterClosedHandler(handler)

  def sse(body: (ActorRef) => Unit)(implicit refFactory: ActorRefFactory): Route = {

    def sseRoute() = (ctx: RequestContext) => {

      val connectionHandler = refFactory.actorOf(
        Props {
          new Actor {
            var closedHandlers: List[() => Unit] = Nil
            ctx.responder ! ChunkedResponseStart(responseStart)
            context.setReceiveTimeout(sseKeepAliveTimeout)

            def receive = {
              case SseMessage(event, data) =>
                val eventString = event.map(ev => s"event: $ev\n").getOrElse("")
                val dataString = data.split("\n").map(d => s"data: $d\n").mkString
                ctx.responder ! MessageChunk(s"$eventString$dataString\n")
              case CloseConnection =>
                ctx.responder ! ChunkedMessageEnd
              case ReceiveTimeout =>
                ctx.responder ! MessageChunk(":\n") // keep connection alive
              case RegisterClosedHandler(handler) => closedHandlers ::= handler
              case _: Http.ConnectionClosed =>
                closedHandlers.foreach(_())
                context.stop(self)
              case _ =>
            }
          }
        }
      )

      body(connectionHandler)
    }

    respondWithMediaType(`text/event-stream`) {
      sseRoute()
    }
  }
}

object SseDirectives extends SseDirectives
