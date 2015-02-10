package io.magnetic.marathon.client

import dispatch._
import io.magnetic.marathon.client.api._
import io.magnetic.vamp_common.text.Text
import org.json4s._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Marathon {

  def info: Option[Info] = {

    implicit val formats = DefaultFormats

    val page = url("http://10.20.79.175:8080/v2/info").GET
    val response = Http(page OK dispatch.as.json4s.Json)

    response onComplete {
      case Success(json) =>

        println(json)
        
        println(json.transformField({
          case JField(name, value) => JField(Text.toLowerCamelCase(name), value)
        }).extract[Info])


      case Failure(error) => println(error)
    }

    None
  }

  def main(arguments: Array[String]): Unit = {
    Marathon.info
  }
}
