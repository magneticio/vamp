package io.vamp.common.http

import com.typesafe.scalalogging.Logger
import dispatch._
import io.vamp.common.text.Text
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object RestClient {

  private val logger = Logger(LoggerFactory.getLogger(RestClient.getClass))

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  def delete(url: String)(implicit executor: ExecutionContext): Future[String] = Http(request(url, Method.DELETE) OK as.String)

  private def request(url: String, method: Method.Value): Req =
    dispatch.url(url).setMethod(method.toString).setHeader("Accept", "application/json").setHeader("Content-Type", "application/json")

  /**
   * JSON REST API HTTP request + JSON
   *
   * @param request Request in format "[METHOD] URL", e.g. "GET https://api.github.com". By default method is GET.
   * @param body Request body. Will be serialized to JSON using default json4s format.
   * @param responsePath Json object path from the response.
   * @param jsonFieldTransformer Json field transformer.
   * @param executor Execution context
   * @param formats Formats Json formats, has a default value, but could be implicitly overridden by whatever is in the context
   * @param mf manifest
   * @tparam A Response type
   * @return Response
   */
  def request[A](request: String, body: AnyRef = None, responsePath: String = "", jsonFieldTransformer: PartialFunction[JField, JField] = defaultJsonFieldTransformer)
                (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {

    val method = Method.values.map(_.toString).find(method => request.startsWith(s"$method ")).getOrElse(Method.GET.toString)
    val url = if (request.startsWith(s"$method ")) request.substring(s"$method ".length) else request

    val httpHeaders = dispatch.url(url)
      .setMethod(method.toString)
      .setHeader("Accept", "application/json")
      .setHeader("Content-Type", "application/json")

    val httpRequest = body match {
      case None =>
        logger.trace(s"req [$request]")
        httpHeaders
      case body:String =>
        logger.trace(s"req [$request] - $body")
        httpHeaders.setBody(body)
      case anyRef =>
        val body = write(anyRef)
        logger.trace(s"req [$request] - $body")
        httpHeaders.setBody(body)
    }

    Http(httpRequest OK dispatch.as.json4s.Json) map { json =>
      logger.trace(s"rsp [$request] - ${compact(render(json))}")
      val jsonObject = if (responsePath.isEmpty) json else json \ responsePath
      jsonObject.transformField(jsonFieldTransformer).extract[A](DefaultFormats, mf)
    }
  }

  def defaultJsonFieldTransformer: PartialFunction[JField, JField] = {
    case JField(name, value) => JField(Text.toLowerCamelCase(name), value)
  }
}
