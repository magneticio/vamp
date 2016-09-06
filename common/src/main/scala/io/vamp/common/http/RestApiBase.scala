package io.vamp.common.http

import akka.http.scaladsl.model.MediaType.Compressible
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{ Directive0, Directives, MalformedHeaderRejection, Route }
import io.vamp.common.json.PrettyJson
import io.vamp.common.notification.NotificationErrorException
import org.json4s.Formats
import org.json4s.native.Serialization._

trait RestApiBase extends Directives {

  implicit val formats: Formats

  protected val `application/x-yaml` = MediaType.customBinary(mainType = "application", subType = "x-yaml", Compressible, fileExtensions = List("yaml"))

  protected def validateOnly = parameters('validate_only.as[Boolean] ? false)

  protected def expandAndOnlyReferences = parameters(('expand_references.as[Boolean] ? false, 'only_references.as[Boolean] ? false))

  protected def pageAndPerPage(perPage: Int = 30) = parameters(('page.as[Long] ? 1, 'per_page.as[Long] ? perPage))

  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  protected def accept(mr: MediaRange*): Directive0 = headerValueByName("Accept").flatMap {
    case actual if actual.split(",").map(_.trim).exists(v ⇒ v.startsWith("*/*") || mr.exists(_.value == v)) ⇒ pass
    case actual ⇒ reject(MalformedHeaderRejection("Accept", s"Only the following media types are supported: ${mr.mkString(", ")}, but not: $actual"))
  }

  protected def contentTypeOnly(mt: MediaType*) = extract(_.request.headers).flatMap[Unit] {
    headers ⇒
      if (headers.exists({
        case `Content-Type`(ContentType(mediaType: MediaType, _)) ⇒ mt.exists(_.value == mediaType.value)
        case _ ⇒ false
      })) pass
      else reject(MalformedHeaderRejection("Content-Type", s"Only the following media types are supported: ${mt.mkString(", ")}"))

  } & cancelRejections(_.isInstanceOf[MalformedHeaderRejection])

  // TODO: implement as a directive
  def respondWith(status: StatusCode, response: Any): Route = {

    def links(uri: Uri, envelope: OffsetResponseEnvelope[_]) = {

      def link(page: Long, param: LinkParam) = {
        val query = Query(uri.query().toMap + ("per_page" -> s"${envelope.perPage}") + ("page" -> s"$page"))
        LinkValue(uri.withoutFragment.withQuery(query), param)
      }

      val lastPage = envelope.total / envelope.perPage + (if (envelope.total % envelope.perPage == 0) 0 else 1)

      val first = link(1, LinkParams.first)
      val last = link(lastPage, LinkParams.last)

      val previous = link(if (envelope.page > 1) envelope.page - 1 else 1, LinkParams.prev)
      val next = link(if (envelope.page < lastPage) envelope.page + 1 else lastPage, LinkParams.next)

      Link(first, previous, next, last)
    }

    response match {
      case envelope: OffsetResponseEnvelope[_] ⇒
        extractUri { uri ⇒
          respondWithHeader(links(uri, envelope)) {
            respondWithHeader(RawHeader("X-Total-Count", s"${envelope.total}")) {
              respondWithHeader(RawHeader("Access-Control-Expose-Headers", "Link, X-Total-Count")) {
                complete(HttpResponse(status = status, entity = write(envelope.response)))
              }
            }
          }
        }

      case _ ⇒ complete(HttpResponse(status = status, entity = toJson(response)))
    }
  }

  private def toJson(any: Any) = any match {
    case notification: NotificationErrorException ⇒ throw notification
    case exception: Exception                     ⇒ throw new RuntimeException(exception)
    case value: PrettyJson                        ⇒ writePretty(value)
    case value: AnyRef                            ⇒ write(value)
    case value                                    ⇒ write(value.toString)
  }
}

//package io.vamp.common.http
//
//import io.vamp.common.json.PrettyJson
//import io.vamp.common.notification.NotificationErrorException
//import org.json4s.Formats
//import org.json4s.native.Serialization._
//import org.yaml.snakeyaml.DumperOptions.FlowStyle
//import org.yaml.snakeyaml.Yaml
//import org.yaml.snakeyaml.nodes.Tag
//import shapeless.HNil
//import spray.http.CacheDirectives.`no-store`
//import spray.http.HttpHeaders._
//import spray.http.HttpMethods._
//import spray.http.MediaTypes._
//import spray.http.MediaTypes.register
//import spray.http.Uri.Query
//import spray.http._
//import spray.httpx.marshalling.{ MarshallingContext, Marshaller, ToResponseMarshaller }
//import spray.routing._
//
//trait RestApiContentTypes {
//  val `application/x-yaml` = register(MediaType.custom(mainType = "application", subType = "x-yaml", compressible = true, binary = true, fileExtensions = Seq("yaml")))
//}
//
//trait RestApiBase extends HttpServiceBase with RestApiPagination with RestApiMarshaller with RestApiContentTypes {
//
//  protected def validateOnly = parameters('validate_only.as[Boolean] ? false)
//
//  protected def expandAndOnlyReferences = parameters(('expand_references.as[Boolean] ? false, 'only_references.as[Boolean] ? false))
//
//  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))
//
//  protected def allowXhrFromOtherHosts = respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))
//
//  protected def accept(mr: MediaRange*): Directive0 = headerValueByName("Accept").flatMap {
//    case actual if actual.split(",").map(_.trim).exists(v ⇒ v.startsWith("*/*") || mr.exists(_.value == v)) ⇒ pass
//case actual ⇒ reject(MalformedHeaderRejection("Accept", s"Only the following media types are supported: ${mr.mkString(", ")}, but not: $actual"))
//}
//
//protected def contentTypeOnly(mt: MediaType*): Directive0 = extract(_.request.headers).flatMap[HNil] {
//headers ⇒
//if (headers.exists({
//case `Content-Type`(ContentType(mediaType: MediaType, _)) ⇒ mt.exists(_.value == mediaType.value)
//case _ ⇒ false
//})) pass
//else reject(MalformedHeaderRejection("Content-Type", s"Only the following media types are supported: ${mt.mkString(", ")}"))
//
//} & cancelAllRejections(ofType[MalformedHeaderRejection])
//
//protected def contentTypeForModification = contentTypeOnly(`application/json`, `application/x-yaml`)
//
//override def delete: Directive0 = super.delete & contentTypeForModification
//
//override def put: Directive0 = super.put & contentTypeForModification
//
//override def post: Directive0 = super.post & contentTypeForModification
//}
//
//trait RestApiPagination {
//  this: HttpServiceBase with RestApiMarshaller ⇒
//
//  def pageAndPerPage(perPage: Int = 30) = parameters(('page.as[Long] ? 1, 'per_page.as[Long] ? perPage))
//
//  // TODO: implement as a Spray Directive
//  def respondWith(status: StatusCode, response: Any): Route = {
//
//    def links(uri: Uri, envelope: OffsetResponseEnvelope[_]) = {
//
//      def link(page: Long, param: Link.Param) = {
//        val query = Query(uri.query.toMap + ("per_page" -> s"${envelope.perPage}") + ("page" -> s"$page"))
//        Link.Value(uri.copy(fragment = None, query = query), param)
//      }
//
//      val lastPage = envelope.total / envelope.perPage + (if (envelope.total % envelope.perPage == 0) 0 else 1)
//
//      val first = link(1, Link.first)
//      val last = link(lastPage, Link.last)
//
//      val previous = link(if (envelope.page > 1) envelope.page - 1 else 1, Link.prev)
//      val next = link(if (envelope.page < lastPage) envelope.page + 1 else lastPage, Link.next)
//
//      Link(first, previous, next, last)
//    }
//
//    respondWithStatus(status) {
//      response match {
//        case envelope: OffsetResponseEnvelope[_] ⇒
//          requestUri { uri ⇒
//            respondWithHeader(links(uri, envelope)) {
//              respondWithHeader(RawHeader("X-Total-Count", s"${envelope.total}")) {
//                respondWithHeader(RawHeader("Access-Control-Expose-Headers", "Link, X-Total-Count")) {
//                  complete(envelope.response)
//                }
//              }
//            }
//          }
//
//        case _ ⇒ complete(response)
//      }
//    }
//  }
//}
//
//trait RestApiMarshaller {
//  this: RestApiContentTypes ⇒
//
//  implicit val formats: Formats
//
//  implicit def marshaller: ToResponseMarshaller[Any] = ToResponseMarshaller.oneOf(`application/json`, `application/x-yaml`)(jsonMarshaller, yamlMarshaller)
//
//  def jsonMarshaller: Marshaller[Any] = Marshaller.of[Any](`application/json`) { (value, contentType, ctx) ⇒
//    marshall(value, contentType, ctx, { value ⇒ toJson(value) })
//  }
//
//  def yamlMarshaller: Marshaller[Any] = Marshaller.of[Any](`application/x-yaml`) { (value, contentType, ctx) ⇒
//    marshall(value, contentType, ctx, { _ ⇒
//      value match {
//        case None ⇒ toJson(None)
//        case some ⇒
//          val yaml = new Yaml()
//          new Yaml().dumpAs(yaml.load(toJson(some)), if (value.isInstanceOf[List[_]]) Tag.SEQ else Tag.MAP, FlowStyle.BLOCK)
//      }
//    })
//  }
//
//  private def toJson(any: Any) = {
//    any match {
//      case notification: NotificationErrorException ⇒ throw notification
//      case exception: Exception                     ⇒ throw new RuntimeException(exception)
//      case value: PrettyJson                        ⇒ writePretty(value)
//      case value: AnyRef                            ⇒ write(value)
//      case value                                    ⇒ write(value.toString)
//    }
//  }
//
//  private def marshall(value: Any, contentType: ContentType, ctx: MarshallingContext, transform: Any ⇒ String) = value match {
//    case entity: HttpEntity ⇒ ctx.marshalTo(entity)
//    case _                  ⇒ ctx.marshalTo(HttpEntity(contentType, transform(value)))
//  }
//}
//
///**
//  * @see https://gist.github.com/joseraya/176821d856b43b1cfe19
//  * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
//  */
//trait CorsSupport {
//  this: HttpServiceBase ⇒
//
//  protected val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)
//
//  protected val optionsCorsHeaders = List(
//    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
//    `Access-Control-Max-Age`(1728000))
//
//  def cors[T]: Directive0 = mapRequestContext { ctx ⇒
//    ctx.withRouteResponseHandling({
//      // It is an option request for a resource that responds to some other method
//      case Rejected(x) if ctx.request.method.equals(HttpMethods.OPTIONS) && x.exists(_.isInstanceOf[MethodRejection]) ⇒
//        val allowedMethods: List[HttpMethod] = x.filter(_.isInstanceOf[MethodRejection]).map(rejection ⇒ {
//          rejection.asInstanceOf[MethodRejection].supported
//        })
//        ctx.complete(HttpResponse().withHeaders(
//          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods: _*) :: allowOriginHeader ::
//            optionsCorsHeaders
//        ))
//    }).withHttpResponseHeadersMapped { headers ⇒
//      allowOriginHeader :: headers
//    }
//  }
//}
