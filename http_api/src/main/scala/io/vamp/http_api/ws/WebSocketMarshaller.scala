package io.vamp.http_api.ws

import io.vamp.common.notification.{ NotificationErrorException, NotificationProvider }
import io.vamp.http_api.notification.BadRequestError
import io.vamp.http_api.ws.Content.ContentType
import io.vamp.model.artifact.Artifact
import io.vamp.model.event.Event
import io.vamp.model.reader.{ YamlLoader, YamlSourceReader }
import io.vamp.model.serialization.CoreSerializationFormat
import org.json4s._
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.nodes.Tag

import scala.reflect.ClassTag

trait WebSocketMarshaller extends YamlLoader {
  this: NotificationProvider ⇒

  import io.vamp.model.reader.YamlSourceReader._

  private implicit val formats: Formats = CoreSerializationFormat.default +
    new UpperCaseEnumSerializer(Action) +
    new UpperCaseEnumSerializer(Content) +
    new UpperCaseEnumSerializer(Status)

  def unmarshall(input: String): List[WebSocketMessage] = try {
    (super.unmarshal(input) match {
      case Right(r) ⇒ r
      case Left(r)  ⇒ r :: Nil
    }).map(unmarshall)
  } catch {
    case NotificationErrorException(n, _) ⇒ WebSocketError(n) :: Nil
    case e: Exception                     ⇒ WebSocketError(invalidYamlException(e)) :: Nil
  }

  def marshall(response: AnyRef): String = {

    def marshall(any: AnyRef, as: Option[ContentType] = None): String = as match {
      case Some(Content.Json) ⇒ write(any)
      case Some(Content.Yaml) ⇒ yaml.dumpAs(yaml.load(write(any)), if (any.isInstanceOf[List[_]]) Tag.SEQ else Tag.MAP, FlowStyle.BLOCK)
      case Some(_)            ⇒ any.toString
      case _                  ⇒ write(any)
    }

    response match {
      case e: WebSocketError                ⇒ marshall(Map("status" -> Status.Error.toString.toUpperCase, "error" -> message(e.error)))
      case r: WebSocketValidMessage         ⇒ marshall(r, Option(r.content))
      case (r: WebSocketResponse, e: Event) ⇒ marshall(r.copy(data = Option(e)), Option(r.content))
      case _                                ⇒ marshall(Map("status" -> Status.Error.toString.toUpperCase, "error" -> "unsupported"))
    }
  }

  private def unmarshall(source: YamlSourceReader): WebSocketMessage = {

    def extract(key: String, enumeration: Enumeration) = {
      val value = source.get[String](key)
      enumeration.values.find(e ⇒ e.toString.toLowerCase == value.toLowerCase) match {
        case Some(v) ⇒ v
        case _       ⇒ throwException(BadRequestError(s"Unsupported $key: $value"))
      }
    }

    val api = source.get[String]("api")
    val path = source.get[String]("path")

    if (api != Artifact.version) throwException(BadRequestError(s"Unsupported API: $api"))

    WebSocketRequest(
      api = api,
      path = if (path.startsWith("/")) path else s"/$path",
      action = extract("action", Action),
      accept = extract("accept", Content),
      content = extract("content", Content),
      transaction = source.get[String]("transaction"),
      data = source.find[String]("data"),
      parameters = source.get[Map[String, AnyRef]]("parameters")
    )
  }
}

class UpperCaseEnumSerializer[E <: Enumeration: ClassTag](enum: E) extends Serializer[E#Value] {

  import JsonDSL._

  def deserialize(implicit format: Formats) = throw new NotImplementedError

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case i: E#Value ⇒ i.toString.toUpperCase
  }
}