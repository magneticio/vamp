package io.vamp.operation.http

import io.vamp.operation.http.Action.ActionType
import io.vamp.operation.http.Content.ContentType
import io.vamp.operation.http.Status.StatusType

sealed trait WebSocketMessage

sealed trait WebSocketValidMessage extends WebSocketMessage {

  def api: String

  def path: String

  def action: ActionType

  def content: ContentType

  def transaction: String

  def data: Option[String]

  def parameters: Map[String, AnyRef]
}

case class WebSocketError(message: String) extends WebSocketMessage {
  val status = Status.Error
}

case class WebSocketRequest(api: String,
                            path: String,
                            action: ActionType,
                            accept: ContentType,
                            content: ContentType,
                            transaction: String,
                            data: Option[String],
                            parameters: Map[String, AnyRef] = Map()) extends WebSocketValidMessage

case class WebSocketResponse(api: String,
                             path: String,
                             action: ActionType,
                             status: StatusType,
                             content: ContentType,
                             transaction: String,
                             data: Option[String],
                             parameters: Map[String, AnyRef] = Map()) extends WebSocketValidMessage

object Action extends Enumeration {
  type ActionType = Value

  val Peek, Put, Remove = Value
}

object Content extends Enumeration {
  type ContentType = Value

  val Json, Yaml, Javascript = Value
}

object Status extends Enumeration {
  type StatusType = Value

  val Ok, Accepted, NoContent, Error = Value
}