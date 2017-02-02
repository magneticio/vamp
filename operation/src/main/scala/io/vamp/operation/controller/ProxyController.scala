package io.vamp.operation.controller

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.{ Path, Query }
import akka.http.scaladsl.model.ws.{ UpgradeToWebSocket, WebSocketRequest }
import akka.http.scaladsl.server.{ RequestContext, RouteResult }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Gateway
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.Future

trait ProxyController extends GatewayDeploymentResolver {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  implicit def timeout: Timeout

  def http(context: RequestContext, path: String)(implicit materializer: Materializer): Future[RouteResult] = {
    wrapper(context, path, http(context))
  }

  def websocket(context: RequestContext, upgrade: UpgradeToWebSocket, path: String)(implicit materializer: Materializer): Future[RouteResult] = {
    wrapper(context, path, websocket(context, upgrade))
  }

  private def wrapper(context: RequestContext, path: String, handle: (Gateway, List[String]) ⇒ Future[RouteResult]) = {
    gatewayFor(path2list(Path(path))).flatMap {
      case Some((gateway, segments)) ⇒ handle(gateway, segments)
      case _                         ⇒ context.reject()
    }
  }

  private def http(context: RequestContext)(gateway: Gateway, segments: List[String])(implicit materializer: Materializer): Future[RouteResult] = {
    logger.info(s"HTTP proxy request: ${gateway.name}${segments.mkString}")
    Source.single(context.request)
      .map(request ⇒ request.withHeaders(
        request.headers.filter(_.renderInRequests())
      ).withUri(
          request.uri.withPath(
          Path(segments.mkString)
        ).withQuery(
            Query(request.uri.rawQueryString.map("?" + _).getOrElse(""))
          ).toRelative
        ))
      .via(Http().outgoingConnection(gateway.service.get.host, gateway.service.get.port.number))
      .runWith(Sink.head)
      .flatMap(context.complete(_))
  }

  private def websocket(context: RequestContext, upgrade: UpgradeToWebSocket)(gateway: Gateway, segments: List[String])(implicit materializer: Materializer): Future[RouteResult] = {
    logger.info(s"WebSocket proxy request: ${gateway.name}${segments.mkString}")
    val request = WebSocketRequest(
      uri = context.request.uri.withScheme("ws").
        withAuthority(gateway.service.get.host, gateway.service.get.port.number).
        withPath(Path(segments.mkString)).
        withQuery(Query(context.request.uri.rawQueryString.map("?" + _).getOrElse(""))),
      extraHeaders = context.request.headers.filter(_.renderInRequests()),
      subprotocol = None
    )
    context.complete(upgrade.handleMessages(Http().webSocketClientFlow(request)))
  }

  private def gatewayFor(segments: List[String]): Future[Option[(Gateway, List[String])]] = {

    def tryWith(length: Int): Future[Option[(Gateway, List[String])]] = {
      (if (segments.size <= length) None else Option(segments.take(length).mkString)).map { name ⇒
        gatewayFor(name).map(_.map(_ → segments.drop(length)))
      }.getOrElse(Future.successful(None))
    }

    List(3, 5).foldLeft(tryWith(1)) { (op1, op2) ⇒
      op1.flatMap {
        case Some((g, l)) if g.proxy.nonEmpty ⇒ Future.successful(Option(g → l))
        case _                                ⇒ tryWith(op2)
      }
    }
  }

  @tailrec
  private def path2list(path: Path, list: List[String] = Nil): List[String] = {
    if (path.isEmpty) list else path2list(path.tail, list :+ path.head.toString)
  }
}
