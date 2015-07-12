package io.vamp.core.container_driver.docker.wrapper

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ExecutionException, Executors, ThreadFactory}

import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig
import com.ning.http.client.{AsyncHandler, AsyncHttpClientConfig, HttpResponseStatus, ProxyServer, Response}
import dispatch.stream.{Strings, StringsByLine}
import dispatch.{FunctionHandler, Http, Req, url}
import io.vamp.core.container_driver.docker.wrapper.model.AuthConfig
import org.jboss.netty.util.HashedWheelTimer
import unisockets.netty.ClientUdsSocketChannelFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception.allCatch
import scala.util.control.NoStackTrace

case class Docker(
                   hostStr: String = Docker.DefaultHost,
                   private val http: Option[(Http, Docker.Closer)] = None,
                   private val _auth: Option[AuthConfig] = None)
                 (implicit ec: ExecutionContext) extends Requests(
  Docker.host(hostStr), http.getOrElse(Docker.http(hostStr)), _auth) {

  def secure(tls: TLS) = copy(
    hostStr = hostStr.replaceFirst("http", "https"),
    http = Some((client.configure(tls.certify), Docker.Closer(close()))))

  def as(config: AuthConfig) = copy(_auth = Some(config))
}


object Docker {

  case class Error(code: Int, message: String)
    extends RuntimeException(message) with NoStackTrace

  object Closed extends RuntimeException("client is closed") with NoStackTrace

  type Handler[T] = AsyncHandler[T]

  trait Closer {
    def close()
  }

  object Closer {
    def apply(closer: => Unit): Closer =
      new Closer {
        def close() = closer
      }
  }

  private[wrapper] trait StreamErrorHandler[T]
    extends AsyncHandler[T] {
    self: AsyncHandler[T] =>
    abstract override def onStatusReceived(status: HttpResponseStatus) =
      if (status.getStatusCode / 100 == 2)
        super.onStatusReceived(status)
      else
        throw Error(status.getStatusCode, "")
  }

  trait Completer {
    def apply[T]
    (handler: Docker.Handler[T]): Future[T]
  }

  abstract class Completion[T: Representation]
  (implicit ec: ExecutionContext) extends Completer {

    def apply(): Future[T] =
      apply(implicitly[Representation[T]].map)

    def apply[TT]
    (f: Response => TT): Future[TT] =
      apply(new FunctionHandler(f) {
        override def onCompleted(response: Response) = {
          if (response.getStatusCode / 100 == 2) f(response)
          else throw Error(
            response.getStatusCode,
            if (response.hasResponseBody) response.getResponseBody else "")
        }
      }).recoverWith {
        case ee: ExecutionException =>
          Future.failed(ee.getCause)
      }
  }

  object Stream {

    trait Stopper {
      def stop(): Unit
    }

    def lines[T: StreamRepresentation]: (T => Unit) => Docker.Handler[Unit] with Stream.Stopper = { f =>
      new StringsByLine[Unit] with StreamErrorHandler[Unit]
        with Stream.Stopper {
        def onStringBy(str: String) {
          f(implicitly[StreamRepresentation[T]].map(str))
        }

        def onCompleted() = ()
      }
    }

    def chunk[T: StreamRepresentation]: (T => Unit) => Docker.Handler[Unit] with Stream.Stopper = { f =>
      new Strings[Unit] with Docker.StreamErrorHandler[Unit]
        with Docker.Stream.Stopper {
        def onString(str: String) {
          f(implicitly[StreamRepresentation[T]].map(str.trim))
        }

        def onCompleted() = ()
      }
    }
  }

  abstract class Stream[T: StreamRepresentation] extends Completer {
    type Handler = T => Unit

    def stream(f: Handler): (Stream.Stopper, Future[Unit]) = {
      val stopper = streamer(f)
      (stopper, apply(stopper))
    }

    protected def streamer: Handler => Docker.Handler[Unit] with Stream.Stopper = Stream.lines
  }

  private def env(name: String) = Option(System.getenv(s"DOCKER_$name".toUpperCase))

  private[wrapper] val UserAgent = s"vamp-docker-driver"
  ///${BuildInfo.version}"
  private[wrapper] val DefaultHeaders = Map("User-Agent" -> UserAgent)
  private[wrapper] val DefaultHost = (for {
    host <- env("HOST")
    uri <- allCatch.opt(new URI(host))
  } yield s"${if (2376 == uri.getPort) "https" else "http"}://${uri.getHost}:${uri.getPort}").getOrElse(
      "unix:///var/run/docker.sock"
    )

  private[wrapper] def host(hostStr: String): Req =
    if (hostStr.startsWith("unix://")) {
      Req(identity).setVirtualHost(hostStr).setProxyServer(new ProxyServer(hostStr, 80))
    } else url(hostStr)

  private[wrapper] def http(host: String): (Http, Closer) = {
    val certs = env("CERT_PATH")
    val verify = env("TLS_VERIFY").exists(_.nonEmpty)
    val http =
      if (host.startsWith("unix://")) {
        lazy val closed = new AtomicBoolean(false)
        lazy val timer = new HashedWheelTimer(new ThreadFactory {
          def newThread(runnable: Runnable): Thread =
            new Thread(runnable) {
              setDaemon(true)
            }
        })
        def shutdown(): Unit =
          if (closed.compareAndSet(false, true)) {
            sockets.releaseExternalResources()
            timer.stop()
          }

        lazy val threads = new ThreadFactory {
          def newThread(runnable: Runnable) =
            new Thread(runnable) {
              setDaemon(true)

              override def interrupt() {
                shutdown()
                super.interrupt()
              }
            }
        }
        lazy val sockets =
          new ClientUdsSocketChannelFactory(
            Executors.newCachedThreadPool(threads),
            Executors.newCachedThreadPool(threads),
            timer
          )
        val http0 = new Http().configure { builder =>
          val config = builder.build()
          val updatedProvider = config.getAsyncHttpProviderConfig match {
            case netty: NettyAsyncHttpProviderConfig =>
              netty.addProperty(
                NettyAsyncHttpProviderConfig.SOCKET_CHANNEL_FACTORY,
                sockets
              )
              netty.setNettyTimer(timer)
              netty
            case dunno =>
              // user has provided an async client non using a netty provider
              dunno
          }
          new AsyncHttpClientConfig.Builder(config)
            .setAsyncHttpClientProviderConfig(updatedProvider)
        }
        (http0, Closer {
          shutdown()
          http0.shutdown()
        })
      }
      else {
        val http0 = new Http
        (http0, Closer(http0.shutdown()))
      }
    (certs, http) match {
      case (Some(path), (client, closer)) =>
        def pem(name: String) = s"$path/$name.pem"
        (client.configure(TLS(pem("key"), pem("cert"), Some(pem("ca")).filter(_ => verify)).certify), closer)
      case _ =>
        http
    }
  }
}


