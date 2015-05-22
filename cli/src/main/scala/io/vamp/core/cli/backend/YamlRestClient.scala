package io.vamp.core.cli.backend

import com.typesafe.scalalogging.Logger
import dispatch._
import io.vamp.common.http.RestClient.Method
import org.json4s._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object YamlRestClient {

  private val logger = Logger(LoggerFactory.getLogger(YamlRestClient.getClass))

  def request[A](request: String, body: Option[String] = None)
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
      case Some(payload) =>
        logger.debug(s"req [$request] - $payload")
        httpHeaders.setBody(payload)
    }

    Http(httpRequest OK dispatch.as.String).asInstanceOf[Future[A]]

  }

}
