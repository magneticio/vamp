package io.vamp.core.operation.workflow

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.FutureSupport
import io.vamp.common.http.RestClient

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class HttpClientContext(ec: ExecutionContext) extends FutureSupport {

  import RestClient._

  implicit val executionContext = ec
  implicit lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.http.timeout").seconds)

  private var body: Any = null
  private var url: Option[String] = None
  private var method: Option[Method.Value] = None
  private var headers: List[(String, String)] = Nil

  def set(name: String, value: String): HttpClientContext = {
    headers = headers :+ (name -> value)
    this
  }

  def send(body: Any): HttpClientContext = {
    this.body = body
    this
  }

  def get(url: String): HttpClientContext = methodUrl(Method.GET, url)

  def post(url: String, body: Any = null): HttpClientContext = methodUrl(Method.POST, url)

  def put(url: String, body: Any = null): HttpClientContext = methodUrl(Method.PUT, url)

  def delete(url: String): HttpClientContext = methodUrl(Method.DELETE, url)

  def string(): String = offload(call(asJson = false)) match {
    case None => ""
    case Some(response) => response.toString
  }

  def json() = offload(call(asJson = true)) match {
    case None => Map()
    case Some(response) => response
  }

  @inline private def methodUrl(method: Method.Value, url: String): HttpClientContext = {
    this.url = Some(url)
    this.method = Some(method)
    this
  }

  @inline private def call(asJson: Boolean): Future[Option[Any]] = (method, url) match {
    case (Some(m), Some(u)) => http(m, headers, u, body, asJson = asJson)
    case _ => throw new RuntimeException(s"HTTP: method or URL not specified.")
  }
}
