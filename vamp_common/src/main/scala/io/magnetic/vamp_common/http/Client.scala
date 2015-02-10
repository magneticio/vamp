package io.magnetic.vamp_common.http

import dispatch._
import io.magnetic.vamp_common.text.Text
import org.json4s._

import scala.concurrent.{ExecutionContext, Future}

object Client {

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  def request[A](url: String, method: Method.Value = Method.GET)(implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A]): Future[A] =
    Http(dispatch.url(url).setMethod(method.toString) OK dispatch.as.json4s.Json) map { json =>
      println(json)

      json.transformField({
        case JField(name, value) => JField(Text.toLowerCamelCase(name), value)
      }).extract[A](DefaultFormats, mf)
    }
}
