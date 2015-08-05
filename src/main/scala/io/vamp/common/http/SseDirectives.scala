package io.vamp.common.http

import akka.actor._
import spray.can.Http
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

  case class RegisterClosedHandler(handler: () => Unit)

  object CloseConnection

  val `text/event-stream` = register(MediaType.custom(mainType = "text", subType = "event-stream", compressible = true, binary = true))

  def responseStart = HttpResponse(headers = Nil, entity = "\n")

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
              case data: String =>
                ctx.responder ! MessageChunk(s"$data\n")
              case CloseConnection =>
                ctx.responder ! ChunkedMessageEnd
              case ReceiveTimeout =>
                ctx.responder ! MessageChunk("\n") // keep connection alive
              case RegisterClosedHandler(handler) => closedHandlers ::= handler
              case _: Http.ConnectionClosed =>
                closedHandlers.foreach(_())
                context.stop(self)
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
