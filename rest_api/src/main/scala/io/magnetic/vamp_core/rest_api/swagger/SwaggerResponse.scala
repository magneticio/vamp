package io.magnetic.vamp_core.rest_api.swagger

import com.typesafe.config.ConfigFactory
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization.read

import scala.io.Source

trait SwaggerResponse {
  lazy val swagger: Swagger = {
    val config = ConfigFactory.load()
    val port = config.getInt("server.port")
    val host = config.getString("server.host")

    implicit val formats = Serialization.formats(NoTypeHints)
    val result = read[Swagger](Source.fromURL(getClass.getResource("/swagger/swagger.json")).bufferedReader())
    result.copy(host = s"$host:$port", basePath = "/api/v1")
  }
}
