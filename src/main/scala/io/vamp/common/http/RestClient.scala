package io.vamp.common.http

import _root_.io.vamp.common.text.Text
import com.typesafe.scalalogging.Logger
import dispatch._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._

object RestClient {

  private val logger = Logger(LoggerFactory.getLogger(RestClient.getClass))

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  val jsonHeaders = List("Accept" -> "application/json", "Content-Type" -> "application/json")

  def get[A](url: String, headers: List[(String, String)] = jsonHeaders)
            (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.GET, url, None, headers)
  }

  def post[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
             (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.POST, url, body, headers)
  }

  def put[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
            (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.PUT, url, body, headers)
  }

  def delete(url: String, headers: List[(String, String)] = jsonHeaders)(implicit executor: ExecutionContext) = {
    http(Method.DELETE, url, None)
  }

  def http[A](method: Method.Value, url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
             (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {

    val requestWithUrl = dispatch.url(url).setMethod(method.toString)
    val requestWithHeaders = headers.foldLeft(requestWithUrl)((http, header) => http.setHeader(header._1, header._2))
    val requestWithBody = body match {
      case str: String =>
        logger.trace(s"req [${method.toString} $url] - $str")
        requestWithHeaders.setBody(str)
      case any: AnyRef if any != null && any != None =>
        val request = write(any)
        logger.trace(s"req [${method.toString} $url] - $request")
        requestWithHeaders.setBody(request)
      case any if any != null && any != None =>
        val request = any.toString
        logger.trace(s"req [${method.toString} $url] - $request")
        requestWithHeaders.setBody(request)
      case _ =>
        logger.trace(s"req [${method.toString} $url]")
        requestWithHeaders
    }

    if (classTag[A].runtimeClass == classOf[Nothing]) {
      Http(requestWithBody OK as.String).map { string =>
        logger.trace(s"rsp [${method.toString} $url] - $string")
        string.asInstanceOf[A]
      }
    } else if (classTag[A].runtimeClass == classOf[String]) {
      Http(requestWithBody OK as.String).map { string =>
        logger.trace(s"rsp [${method.toString} $url] - $string")
        string.asInstanceOf[A]
      }
    } else {
      Http(requestWithBody OK dispatch.as.json4s.Json).map { json =>
        logger.trace(s"rsp [${method.toString} $url] - ${compact(render(json))}")
        json.extract[A](formats, mf)
      }
    }
  }

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
  @deprecated("use: http() or HTTP method specific calls.", "0.7.8")
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
      case body: String =>
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
