package io.vamp.http_api

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, IoC }
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.notification.NotificationProvider
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.operation.config.ConfigurationLoaderActor
import io.vamp.persistence.KeyValueStoreActor

import scala.concurrent.Future

trait SystemRoute extends SystemController {
  this: ExecutionContextProvider with ActorSystemProvider with HttpApiDirectives with NotificationProvider ⇒

  implicit def timeout: Timeout

  val systemRoutes = {
    pathPrefix("vga") {
      path(Segment / Segment / "template") { (kind, name) ⇒
        pathEndOrSingleSlash {
          get {
            onSuccess(vgaTemplate(kind, name)) { result ⇒
              respondWith(OK, result)
            }
          } ~ (put | post) {
            entity(as[String]) { request ⇒
              validateOnly { validateOnly ⇒
                onSuccess(vgaTemplateUpdate(kind, name, request, validateOnly)) { result ⇒
                  respondWith(Accepted, result)
                }
              }
            }
          }
        }
      } ~ get {
        path(Segment / Segment / ("configuration" | "config")) { (kind, name) ⇒
          pathEndOrSingleSlash {
            onSuccess(vgaConfig(kind, name)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      }
    }
  } ~ pathPrefix("configuration" | "config") {
    get {
      parameters('type.as[String] ? "") { `type` ⇒
        parameters('flatten.as[Boolean] ? false) { flatten ⇒
          path(Segment) { key: String ⇒
            pathEndOrSingleSlash {
              onSuccess(configuration(`type`, flatten, key)) { result ⇒
                respondWith(OK, result)
              }
            }
          } ~ pathEndOrSingleSlash {
            onSuccess(configuration(`type`, flatten)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      }
    } ~ (put | post) {
      entity(as[String]) { request ⇒
        validateOnly { validateOnly ⇒
          onSuccess(configurationUpdate(request, validateOnly)) { result ⇒
            respondWith(Accepted, result)
          }
        }
      }
    }
  }
}

trait SystemController {
  this: NotificationProvider with ExecutionContextProvider with ActorSystemProvider ⇒

  def vgaTemplate(kind: String, name: String): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout()
    IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.GetTemplate(kind, name) map {
      case result: String ⇒ HttpEntity(result)
      case _              ⇒ HttpEntity("")
    }
  }

  def vgaTemplateUpdate(kind: String, name: String, template: String, validateOnly: Boolean): Future[Any] = {
    if (validateOnly) Future.successful(true)
    else {
      implicit val timeout = KeyValueStoreActor.timeout()
      IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.SetTemplate(kind, name, template)
    }
  }

  def vgaConfig(kind: String, name: String): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout()
    IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.GetConfiguration(kind, name) map {
      case result: String ⇒ HttpEntity(result)
      case _              ⇒ HttpEntity("")
    }
  }

  def configuration(`type`: String, flatten: Boolean, key: String = "") = {
    implicit val timeout = ConfigurationLoaderActor.timeout()
    IoC.actorFor[ConfigurationLoaderActor] ? ConfigurationLoaderActor.Get(`type`, flatten, key) map {
      case m: Map[_, _] if m.isEmpty ⇒ None
      case other                     ⇒ other
    }
  }

  def configurationUpdate(input: String, validateOnly: Boolean) = {
    implicit val timeout = ConfigurationLoaderActor.timeout()
    IoC.actorFor[ConfigurationLoaderActor] ? ConfigurationLoaderActor.Set(input, validateOnly)
  }
}
