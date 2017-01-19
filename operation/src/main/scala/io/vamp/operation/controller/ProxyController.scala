package io.vamp.operation.controller

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.{ Path, Query }
import akka.http.scaladsl.server.{ RequestContext, RouteResult }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Gateway

import scala.annotation.tailrec
import scala.concurrent.Future

trait ProxyController extends GatewayDeploymentResolver {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  implicit def timeout: Timeout

  def strip: Int

  def proxy(context: RequestContext, path: String)(implicit materializer: Materializer): Future[RouteResult] = {
    gatewayFor(path2list(Path(path))).flatMap {
      case Some((gateway, segments)) if gateway.service.nonEmpty ⇒

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

      case _ ⇒ context.reject()
    }
  }

  private def gatewayFor(segments: List[String]): Future[Option[(Gateway, List[String])]] = {

    def name(length: Int): Option[String] = if (segments.size <= length) None else Option(segments.take(length).mkString)

    def tryWith(length: Int): Future[Option[(Gateway, List[String])]] = {
      name(length).map { n ⇒
        gatewayFor(n).map(_.map(_ → segments.drop(length)))
      }.getOrElse(Future.successful(None))
    }

    List(3, 5).foldLeft(tryWith(1)) { (op1, op2) ⇒
      op1.flatMap {
        case Some((g, l)) ⇒ Future.successful(Option(g → l))
        case _            ⇒ tryWith(op2)
      }
    }
  }

  @tailrec
  private def path2list(path: Path, list: List[String] = Nil): List[String] = {
    if (path.isEmpty) list else path2list(path.tail, list :+ path.head.toString)
  }
}
