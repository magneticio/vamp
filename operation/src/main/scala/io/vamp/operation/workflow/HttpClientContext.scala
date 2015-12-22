package io.vamp.operation.workflow

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class HttpClientContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  import RestClient._

  implicit lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.operation.workflow.http.timeout").seconds)

  private var body: Any = null
  private var url: Option[String] = None
  private var method: Option[Method.Value] = None
  private var headers: List[(String, String)] = RestClient.acceptEncodingIdentity :: Nil

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
    headers = RestClient.acceptEncodingIdentity :: Nil
    this
  }

  private def methodUrl(method: Method.Value, url: String): HttpClientContext = {
    this.url = Some(url)
    this.method = Some(method)
    this
  }

  private def request(asJson: Boolean, default: Any) = serialize {
    (method, url) match {
      case (Some(m), Some(u)) ⇒
        (if (asJson) http[Any](m, u, body, headers) else http[String](m, u, body, headers)) map {
          case response ⇒
            reset()
            response match {
              case e: Throwable ⇒
                logger.error(e.getMessage, e)
                None
              case other ⇒ other
            }
        }

      case _ ⇒ throw new RuntimeException(s"HTTP: method or URL not specified.")
    }
  }
}
