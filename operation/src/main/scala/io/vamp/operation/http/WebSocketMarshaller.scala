package io.vamp.operation.http

import io.vamp.model.artifact.Artifact

trait WebSocketMarshaller {

  def unmarshall(input: String): WebSocketRequest = {
    WebSocketRequest(Artifact.version, "/", Action.Put, Content.Yaml, Content.Yaml, "transaction:xxx", Option(input))
  }

  def marshall(response: WebSocketResponse): String = s" >>> ${response.data.getOrElse("")}"
}
