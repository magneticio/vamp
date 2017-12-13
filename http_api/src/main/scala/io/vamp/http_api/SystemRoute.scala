package io.vamp.http_api

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods.{ POST, PUT }
import akka.http.scaladsl.model.StatusCodes._
import akka.pattern.ask
import io.vamp.common.akka.IoC
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.{ ConfigFilter, Namespace }
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.operation.config.ConfigurationLoaderActor
import io.vamp.operation.controller.AbstractController
import io.vamp.persistence.KeyValueStoreActor

import scala.concurrent.Future

trait SystemRoute extends AbstractRoute with SystemController {
  this: HttpApiDirectives ⇒

  def systemRoutes(implicit namespace: Namespace) = vgaRoutes ~ configRoutes

  def vgaRoutes(implicit namespace: Namespace) = {
    pathPrefix("vga") {
      path(Segment / Segment / "template") { (kind, name) ⇒
        pathEndOrSingleSlash {
          get {
            onSuccess(vgaTemplate(kind, name)) { result ⇒
              respondWith(OK, result)
            }
          } ~ (method(PUT) | method(POST)) {
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
  }

  def configRoutes(implicit namespace: Namespace) = {
    pathPrefix("configuration" | "config") {
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
      } ~ (method(PUT) | method(POST)) {
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

  def mesosConfigRoute(implicit namespace: Namespace) = {
    pathPrefix("mesosconfig") {
      get {
        pathEndOrSingleSlash {
          onSuccess(Future.successful(io.vamp.common.Config.export(io.vamp.common.Config.Type.applied, true)(namespace))) { result ⇒
            {
              val configKeyForMesos = result.get("vamp.container-driver.mesos.url")
              configKeyForMesos match {
                case None           ⇒ respondWith(InternalServerError, "Cannot Find Mesos Url")
                case Some(mesosUrl) ⇒ respondWith(OK, mesosUrl)
              }
            }
          }
        }
      }
    }
  }
}

trait SystemController extends AbstractController {

  def vgaTemplate(kind: String, name: String)(implicit namespace: Namespace): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout()
    IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.GetTemplate(kind, name) map {
      case result: String ⇒ HttpEntity(result)
      case _              ⇒ HttpEntity("")
    }
  }

  def vgaTemplateUpdate(kind: String, name: String, template: String, validateOnly: Boolean)(implicit namespace: Namespace): Future[Any] = {
    if (validateOnly) Future.successful(true)
    else {
      implicit val timeout = KeyValueStoreActor.timeout()
      IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.SetTemplate(kind, name, template) map { _ ⇒ HttpEntity(template) }
    }
  }

  def vgaConfig(kind: String, name: String)(implicit namespace: Namespace): Future[Any] = {
    implicit val timeout = KeyValueStoreActor.timeout()
    IoC.actorFor[GatewayDriverActor] ? GatewayDriverActor.GetConfiguration(kind, name) map {
      case result: String ⇒ HttpEntity(result)
      case _              ⇒ HttpEntity("")
    }
  }

  def configuration(`type`: String, flatten: Boolean, key: String = "")(implicit namespace: Namespace) = {
    implicit val timeout = ConfigurationLoaderActor.timeout()
    val filter = ConfigFilter({ (k, _) ⇒
      k.startsWith("vamp.") && (key.isEmpty || k == key)
    })
    IoC.actorFor[ConfigurationLoaderActor] ? ConfigurationLoaderActor.Get(`type`, flatten, filter) map {
      case m: Map[_, _] if m.isEmpty ⇒ None
      case other                     ⇒ other
    }
  }

  def configurationUpdate(input: String, validateOnly: Boolean)(implicit namespace: Namespace) = {
    implicit val timeout = ConfigurationLoaderActor.timeout()
    val filter = ConfigFilter({ (k, _) ⇒
      !k.startsWith("vamp.http-api.")
    })
    IoC.actorFor[ConfigurationLoaderActor] ? ConfigurationLoaderActor.Set(input, filter, validateOnly)
  }
}
