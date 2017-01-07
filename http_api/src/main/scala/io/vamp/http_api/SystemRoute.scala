package io.vamp.http_api

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.config.Config
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.http_api.notification.BadRequestError
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

trait SystemRoute extends SystemController {
  this: ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  val systemRoutes = {
    pathPrefix("haproxy") {
      pathEndOrSingleSlash {
        failWith(reportException(BadRequestError("No HAProxy version specified.")))
      } ~ pathPrefix(Segment) { version ⇒
        onSuccess(haproxy(version)) { result ⇒
          respondWith(OK, result)
        }
      }
    } ~ pathPrefix("configuration" | "config") {
      get {
        path(Segment) { key: String ⇒
          pathEndOrSingleSlash {
            onSuccess(configuration(key)) { result ⇒
              respondWith(OK, result)
            }
          }
        } ~ pathEndOrSingleSlash {
          onSuccess(configuration()) { result ⇒
            respondWith(OK, result)
          }
        }
      }
    }
  }
}

trait SystemController {
  this: NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def haproxy(version: String): Future[Any] = {
    if (HaProxyGatewayMarshaller.path().last == version) {
      implicit val timeout = KeyValueStoreActor.timeout()
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(GatewayDriverActor.root :: HaProxyGatewayMarshaller.path()) map {
        case Some(result: String) ⇒ HttpEntity(result)
        case _                    ⇒ HttpEntity("")
      }
    }
    else Future.successful(HttpEntity(""))
  }

  def configuration(key: String = "") = Future.successful {
    val entries = Config.entries()().filter {
      case (k, _) ⇒ k.startsWith("vamp.")
    }
    if (key.nonEmpty) entries.get(key) else entries
  }
}
