package io.vamp.core.operation.workflow

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.FutureSupport
import io.vamp.common.http.RestClient
import io.vamp.core.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class HttpClientContext(scheduledWorkflow: ScheduledWorkflow)(implicit executionContext: ExecutionContext) extends ScriptingContext(scheduledWorkflow) with FutureSupport {

  import RestClient._

  implicit lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.http-timeout").seconds)

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

  def string() = request(asJson = false, "")

  def json() = request(asJson = true, Map())

  def reset() = {
    body = null
    url = None
    method = None
    headers = Nil
  }

  @inline private def methodUrl(method: Method.Value, url: String): HttpClientContext = {
    this.url = Some(url)
    this.method = Some(method)
    this
  }

  @inline private def request(asJson: Boolean, default: Any) = {
    val response = offload((method, url) match {
      case (Some(m), Some(u)) => http(m, headers, u, body, asJson = asJson)
      case _ => throw new RuntimeException(s"HTTP: method or URL not specified.")
    })
    reset()
    response match {
      case None => default
      case Some(result) => result
    }
  }
}
