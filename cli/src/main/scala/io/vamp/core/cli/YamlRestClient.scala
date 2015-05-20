package io.vamp.core.cli

import com.typesafe.scalalogging.Logger
import dispatch._
import io.vamp.common.http.RestClient.Method
import org.json4s._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object YamlRestClient {

  private val logger = Logger(LoggerFactory.getLogger(YamlRestClient.getClass))

  def request[A](request: String, body: AnyRef = None)
                (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {

    val method = Method.values.map(_.toString).find(method => request.startsWith(s"$method ")).getOrElse(Method.GET.toString)
    val url = if (request.startsWith(s"$method ")) request.substring(s"$method ".length) else request

    val httpHeaders = dispatch.url(url)
      .setMethod(method.toString)
      .setHeader("Accept", "application/x-yaml")
      .setHeader("Content-Type", "application/x-yaml")

    val httpRequest = body match {
      case None =>
        logger.trace(s"req [$request]")
        httpHeaders
      case anyRef =>
        val body = write(anyRef)
        logger.trace(s"req [$request] - $body")
        httpHeaders.setBody(body)
    }

    Http(httpRequest OK dispatch.as.String).asInstanceOf[Future[A]]

  }

}
