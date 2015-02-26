package io.magnetic.vamp_core.rest_api

import akka.actor.ActorRefFactory
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.ActorRefFactoryExecutionContextProvider
import io.magnetic.vamp_core.rest_api.swagger.Swagger
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats, Formats, NoTypeHints}
import spray.http.StatusCodes._
import spray.httpx.Json4sSupport
import spray.routing._

import scala.io.Source

class SwaggerRoute(val actorRefFactory: ActorRefFactory) extends ApiRoute with Json4sSupport with ActorRefFactoryExecutionContextProvider {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit val context = actorRefFactory
  implicit val formats = Serialization.formats(NoTypeHints)

  lazy val swagger: Swagger = {
    val config = ConfigFactory.load()
    val port = config.getInt("server.port")
    val host = config.getString("server.host")

    val result = read[Swagger](Source.fromURL(getClass.getResource("/swagger/swagger.json")).bufferedReader())
    result.copy(host = s"$host:$port", basePath = "/api/v1")
  }

  def route: Route = {
    path("swagger.json") {
      complete(OK, swagger)
    } ~ path("docs") {
      complete(OK, swagger)
    } ~ path("spec") {
      complete(OK, swagger)
    }
  }
}
