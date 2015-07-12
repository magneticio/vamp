package io.vamp.core.container_driver.docker.wrapper

import dispatch.{Http, Req}
import io.vamp.core.container_driver.docker.wrapper.methods._
import io.vamp.core.container_driver.docker.wrapper.model.AuthConfig

import scala.concurrent.{ExecutionContext, Future}

abstract class Requests(val host: Req, http: (Http, Docker.Closer), protected val authConfig: Option[AuthConfig])
                       (protected implicit val ec: ExecutionContext) extends Util with Containers with Images with Auth with Generic {

  protected val (client, closer) = http

  def close() = closer.close()

  def stream[A: StreamRepresentation](req: Req): Docker.Stream[A] = new Docker.Stream[A] {
    def apply[T](handler: Docker.Handler[T]): Future[T] =
      request(req)(handler)
  }

  def complete[A: Representation](req: Req): Docker.Completion[A] = new Docker.Completion[A] {
    override def apply[T](handler: Docker.Handler[T]) =
      request(req)(handler)
  }

  def request[T](req: Req)(handler: Docker.Handler[T]): Future[T] =
    if (client.client.isClosed) Future.failed(Docker.Closed)
    else client(req <:< Docker.DefaultHeaders > handler)
}
