package io.vamp.common.http

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaType.Compressible
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{ HttpEntity, _ }
import akka.http.scaladsl.server.{ Directive0, Directives, MalformedHeaderRejection, Route }
import ch.megard.akka.http.cors.{ CorsDirectives, CorsSettings, HttpHeaderRange }
import io.vamp.common.json.PrettyJson
import io.vamp.common.notification.NotificationErrorException
import org.json4s.Formats
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.collection.immutable.Seq

object HttpApiDirectives {
  val `application/x-yaml` = MediaType.customBinary(mainType = "application", subType = "x-yaml", Compressible, fileExtensions = List("yaml"))
}

trait HttpApiDirectives extends Directives with CorsDirectives {

  import HttpApiDirectives._

  implicit val formats: Formats

  protected def validateOnly = parameters('validate_only.as[Boolean] ? false)

  protected def blueprintName = parameter('name.?)

  protected def expandAndOnlyReferences = parameters(('expand_references.as[Boolean] ? false, 'only_references.as[Boolean] ? false))

  protected def pageAndPerPage(perPage: Int = 30) = parameters(('page.as[Long] ? 1, 'per_page.as[Long] ? perPage))

  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  protected def accept(mr: MediaRange*): Directive0 = headerValueByName("Accept").flatMap {
    case actual if actual.split(",").map(_.trim).exists(v ⇒ v.startsWith("*/*") || mr.exists(_.value == v)) ⇒ pass
    case actual ⇒ reject(MalformedHeaderRejection("Accept", s"Only the following media types are supported: ${mr.mkString(", ")}, but not: $actual"))
  }

  protected def contentTypeOnly(mt: MediaType*) = extract(_.request.entity.contentType).flatMap[Unit] {

    case contentType if mt.exists(_.value == contentType.mediaType.value) ⇒ pass

    case _ ⇒ reject(MalformedHeaderRejection("Content-Type", s"Only the following media types are supported: ${mt.mkString(", ")}"))

  } & cancelRejections(_.isInstanceOf[MalformedHeaderRejection])

  protected def contentTypeForModification = contentTypeOnly(`application/json`, `application/x-yaml`)

  override def put: Directive0 = super.put & contentTypeForModification

  override def post: Directive0 = super.post & contentTypeForModification

  def cors(): Directive0 = cors(
    CorsSettings.Default(
      allowGenericHttpRequests = true,
      allowCredentials = true,
      allowedOrigins = HttpOriginRange.*,
      allowedHeaders = HttpHeaderRange.*,
      allowedMethods = Seq(GET, POST, HEAD, OPTIONS, DELETE, PUT),
      exposedHeaders = List("Link", "X-Total-Count"),
      maxAge = Some(30 * 60)
    )
  )

  protected def respondWith(status: StatusCode, response: Any): Route = {

    def links(uri: Uri, envelope: OffsetResponseEnvelope[_]) = {

      def link(page: Long, param: LinkParam) = {
        val query = Query(uri.query().toMap + ("per_page" → s"${envelope.perPage}") + ("page" → s"$page"))
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
        extractRequest { request ⇒
          respondWithHeader(links(request.uri, envelope)) {
            respondWithHeader(RawHeader("X-Total-Count", s"${envelope.total}")) {
              complete(marshal(request, status, envelope.response))
            }
          }
        }

      case entity: ResponseEntity ⇒ complete(HttpResponse(status = status, entity = entity))

      case _ ⇒
        extractRequest { request ⇒
          complete(marshal(request, status, response))
        }
    }
  }

  private def marshal(request: HttpRequest, status: StatusCode, some: Any): HttpResponse = status match {
    case NotFound  ⇒ HttpResponse(status = NotFound)
    case NoContent ⇒ HttpResponse(status = NoContent)
    case _ ⇒
      val contentType = request.headers.find(_.name == "Accept") match {
        case Some(header) if header.value.startsWith(`application/x-yaml`.value) ⇒ `application/x-yaml`
        case _ ⇒ `application/json`
      }
      val (as: ContentType, data: String) = contentType match {
        case `application/x-yaml` ⇒ ContentType(`application/x-yaml`) → toYaml(some)
        case _                    ⇒ ContentTypes.`application/json` → toJson(some)
      }
      HttpResponse(status = status, entity = HttpEntity(as, data.getBytes))
  }

  protected def toYaml(some: Any): String = {
    if (some != None) {
      val yaml = new Yaml()
      yaml.dumpAs(yaml.load(toJson(some)), if (some.isInstanceOf[List[_]]) Tag.SEQ else Tag.MAP, FlowStyle.BLOCK)
    }
    else ""
  }

  private def toJson(some: Any) = some match {
    case notification: NotificationErrorException ⇒ throw notification
    case exception: Exception                     ⇒ throw new RuntimeException(exception)
    case value: PrettyJson                        ⇒ writePretty(value)
    case value: AnyRef                            ⇒ write(value)
    case value                                    ⇒ write(value.toString)
  }
}
