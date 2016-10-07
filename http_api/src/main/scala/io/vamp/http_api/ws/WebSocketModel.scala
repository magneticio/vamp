package io.vamp.http_api.ws

import io.vamp.common.notification.Notification
import io.vamp.http_api.ws.Action.ActionType
import io.vamp.http_api.ws.Content.ContentType
import io.vamp.http_api.ws.Status.StatusType

object WebSocketMessage {
  val pathDelimiter = '/'
}

sealed trait WebSocketMessage

sealed trait WebSocketValidMessage extends WebSocketMessage {

  def api: String

  def path: String

  def action: ActionType

  def content: ContentType

  def transaction: String

  def data: Option[AnyRef]

  def parameters: Map[String, AnyRef]
}

case class WebSocketError(error: Notification) extends WebSocketMessage

case class WebSocketRequest(api: String,
                            path: String,
                            action: ActionType,
                            accept: ContentType,
                            content: ContentType,
                            transaction: String,
                            data: Option[String],
                            parameters: Map[String, AnyRef]) extends WebSocketValidMessage {

  val eventStream = action == Action.Peek && path == "/events/stream"
}

case class WebSocketResponse(api: String,
                             path: String,
                             action: ActionType,
                             status: StatusType,
                             content: ContentType,
                             transaction: String,
                             data: Option[AnyRef],
                             parameters: Map[String, AnyRef]) extends WebSocketValidMessage

object Action extends Enumeration {
  type ActionType = Value

  val Peek, Put, Remove = Value
}

object Content extends Enumeration {
  type ContentType = Value

  val Json, Yaml, PlainText, Javascript = Value
}

object Status extends Enumeration {
  type StatusType = Value

  val Ok, Accepted, NoContent, Error = Value
}