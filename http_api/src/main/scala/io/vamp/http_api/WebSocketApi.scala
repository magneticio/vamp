package io.vamp.http_api

import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.scaladsl.Flow

import scala.concurrent.Future

trait WebSocketApi {

  private val parallelism = 4

  val websocket = {
    Flow[Message].collect {
      case TextMessage.Strict(message) ⇒ message
    }.mapAsync[String](parallelism)(message ⇒ response(message)).map {
      message: String ⇒ TextMessage.Strict(message)
    }
  }

  private def response(message: String): Future[String] = Future.successful(s"response: $message")
}
