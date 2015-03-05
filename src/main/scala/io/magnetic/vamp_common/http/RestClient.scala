package io.magnetic.vamp_common.http

import dispatch._
import io.magnetic.vamp_common.text.Text
import org.json4s._
import org.json4s.native.Serialization._

import scala.concurrent.{ExecutionContext, Future}

object RestClient {

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  /**
   * JSON REST API HTTP request + JSON 
   *
   * @param request Request in format "[METHOD] URL", e.g. "GET https://api.github.com". By default method is GET.
   * @param body Request body, optional. Will be serialized to JSON using default json4s format.
   * @param responsePath Json object path from the response.
   * @param jsonFieldTransformer Json field transformer.
   * @param executor Execution context
   * @param mf manifest
   * @tparam A Response type
   * @return Response
   */
  def request[A](request: String, body: Option[AnyRef] = None, responsePath: String = "", jsonFieldTransformer: PartialFunction[JField, JField] = defaultJsonFieldTransformer)
                (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A]): Future[A] = {

    val method = Method.values.map(_.toString).find(method => request.startsWith(s"$method ")).getOrElse(Method.GET.toString)
    val url = if (request.startsWith(s"$method ")) request.substring(s"$method ".length) else request

    val httpHeaders = dispatch.url(url)
      .setMethod(method.toString)
      .setHeader("Accept", "application/json")
      .setHeader("Content-Type", "application/json")

    val httpRequest = body match {
      case None => httpHeaders
      case Some(anyRef) =>
        implicit val formats = DefaultFormats
        httpHeaders.setBody(write(anyRef))
    }

    Http(httpRequest OK dispatch.as.json4s.Json) map { json =>
      val jsonObject = if (responsePath.isEmpty) json else json \ responsePath
      jsonObject.transformField(jsonFieldTransformer).extract[A](DefaultFormats, mf)
    }
  }

  def defaultJsonFieldTransformer: PartialFunction[JField, JField] = {
    case JField(name, value) => JField(Text.toLowerCamelCase(name), value)
  }
}
