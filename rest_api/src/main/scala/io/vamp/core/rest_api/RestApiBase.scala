package io.vamp.core.rest_api

import shapeless.HNil
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`, `Content-Type`}
import spray.http.MediaType
import spray.http.MediaTypes._
import spray.routing._

trait RestApiBase extends HttpServiceBase {

  val `application/x-yaml` = register(MediaType.custom(mainType = "application", subType = "x-yaml", compressible = true, binary = true, fileExtensions = Seq("yaml")))

  protected def noCachingAllowed = respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  protected def allowXhrFromOtherHosts = respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  protected def contentTypeOnly(mt: MediaType*): Directive0 = extract(_.request.headers).flatMap[HNil] {
    case headers if mt.exists(t => headers.contains(`Content-Type`(t))) => pass
    case _ => reject(MalformedHeaderRejection("Content-Type", s"Only the following media types are supported: ${mt.mkString(", ")}"))
  } & cancelAllRejections(ofType[MalformedHeaderRejection])

  protected def contentTypeForModification = contentTypeOnly(`application/json`, `application/x-yaml`)

  override def delete: Directive0 = super.delete & contentTypeForModification

  override def put: Directive0 = super.put & contentTypeForModification

  override def post: Directive0 = super.post & contentTypeForModification
}
